package com.malgeum.geo.domain.domain.order.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.malgeum.geo.domain.domain.client.entity.Client;
import com.malgeum.geo.domain.domain.client.repository.ClientRepository;
import com.malgeum.geo.domain.domain.analysisjob.entity.AnalysisJob;
import com.malgeum.geo.domain.domain.analysisjob.entity.AnalysisJobStatus;
import com.malgeum.geo.domain.domain.analysisjob.repository.AnalysisJobRepository;
import com.malgeum.geo.domain.domain.analysisjob.service.AnalysisJobService;
import com.malgeum.geo.domain.domain.order.dto.GeoOrderRequest;
import com.malgeum.geo.domain.domain.order.entity.Order;
import com.malgeum.geo.domain.domain.order.repository.OrderRepository;
import com.malgeum.geo.global.common.ApiException;
import com.malgeum.geo.global.common.DataNotFoundException;
import com.malgeum.geo.domain.pricing.account.service.PricingService;
import com.malgeum.geo.domain.pricing.academy.entity.Academy;
import com.malgeum.geo.domain.domain.order.entity.PartnerOrder;
import com.malgeum.geo.domain.domain.order.repository.PartnerOrderRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {
    private final OrderRepository orderRepository;
    private final ClientRepository clientRepository;
    private final AnalysisJobService analysisJobService;
    private final AnalysisJobRepository analysisJobRepository;
    private final PricingService pricingService;
    private final PartnerOrderRepository partnerOrderRepository;

    @Transactional
    public Long acceptOrder(GeoOrderRequest orderRequest) {
        if (orderRequest.parentOrderId() != null) { // 재평가 주문이면 다른 정보들이 이미 원 주문에서 복사되므로 무시하고 재평가 처리
            return acceptReevaluation(orderRequest.parentOrderId());
        }
        Order savedOrder = saveOrder(orderRequest);
        analysisJobService.enqueue(savedOrder);
        return savedOrder.getId();
    }

    // 개선안 적용 후 재평가. 원 주문을 복사한 새 주문을 만들고, 라이브 페이지를 다시 수집·채점한다.
    @Transactional
    public Long acceptReevaluation(Long parentOrderId) {
        Long clientId = Long.valueOf(SecurityContextHolder.getContext().getAuthentication().getName());

        // 잠금 아래에서 진행 중 여부·횟수를 확인해야 동시 요청이 중복 생성되지 않는다.
        Order parentOrder = orderRepository.findWithLockById(parentOrderId)
                .filter(order -> order.getClient().getId().equals(clientId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND",
                        "의뢰를 찾을 수 없습니다."));
        AnalysisJob parentAnalysisJob = analysisJobRepository.findByOrderId(parentOrder.getId()).orElse(null);

        if (!parentOrder.isWithImprovement() || parentOrder.isReevaluation()
                || parentAnalysisJob == null || parentAnalysisJob.getStatus() != AnalysisJobStatus.SUCCEEDED) {
            throw new ApiException(HttpStatus.CONFLICT, "REEVAL_NOT_ALLOWED", "재평가할 수 없는 의뢰입니다.");
        }
        LocalDateTime expiresAt = ReevaluationPolicy.expiresAt(parentAnalysisJob);
        if (expiresAt == null || LocalDateTime.now().isAfter(expiresAt)) {
            throw new ApiException(HttpStatus.CONFLICT, "REEVAL_WINDOW_EXPIRED", "재평가 기한이 지났습니다.");
        }
        List<AnalysisJob> reevaluationJobs = analysisJobRepository.findAllByOrder_ParentOrder_Id(parentOrder.getId());
        if (reevaluationJobs.stream().anyMatch(AnalysisJob::isInProgress)) {
            throw new ApiException(HttpStatus.CONFLICT, "REEVAL_IN_PROGRESS", "재평가가 진행 중입니다.");
        }
        if (ReevaluationPolicy.calculateRemainingReevaluationCount(reevaluationJobs) == 0) {
            throw new ApiException(HttpStatus.CONFLICT, "REEVAL_LIMIT_EXCEEDED", "포함된 재평가를 모두 사용했습니다.");
        }

        // 실패한 재평가도 번호를 차지. 순서는 1부터 하나씩 오름차순
        Order reevaluation = orderRepository.save(Order.createDuplicateForReevaluation(parentOrder, reevaluationJobs.size() + 1));
        analysisJobService.enqueue(reevaluation);
        log.info("[OrderService] 재평가 주문 접수 완료 - OrderID: {}, parentOrderID: {}",
                reevaluation.getId(), parentOrder.getId());
        return reevaluation.getId();
    }

    private Order saveOrder(GeoOrderRequest orderRequest) {
        String clientIdStr = SecurityContextHolder.getContext().getAuthentication().getName();
        Long clientId = Long.valueOf(clientIdStr);

        log.info("[OrderService] 현재 로그인중인 클라이언트 확인 완료 - ClientID: {}", clientId);

        Client client = clientRepository.findById(clientId)
                .orElseThrow(() -> new IllegalArgumentException("유효하지 않은 고객입니다."));
        Order order = createOrder(client, orderRequest);
        Academy academy = pricingService.authorizeOrder(order, orderRequest.improvementSource());
        Order savedOrder = orderRepository.save(order);
        if (academy != null) {
            partnerOrderRepository.save(PartnerOrder.builder().order(savedOrder).academy(academy).build());
        }
        log.info("[OrderService] 새로운 분석 주문 접수 완료 - OrderID: {}", savedOrder.getId());

        return savedOrder;
    }

    @Transactional(readOnly = true)
    public Order getOrder(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new DataNotFoundException("Order not found. id=" + orderId));
    }

    //TODO: 이전에 입력한적 있는 Url인 경우에 해당 주문과 매핑할지 말지 묻기? (유료 고객인 경우에만)
    //새 분석으로 완전 덮어쓰기 vs 이전 분석은 남기고 새 분석하기

    //TODO: 재평가 요청 시 AI 호출 전에 스크래핑만 먼저 해서 JSON-LD가 parentOrder과 달라졌는지 확인(해시 비교)
    //같으면 "아직 개선안이 반영되지 않은 것 같아요" 확인 문구를 띄우고 크레딧 차감 없이 반환

    public Order createOrder(Client client, GeoOrderRequest orderRequest) {
        return Order.builder()
                .client(client)
                .targetUrl(orderRequest.targetUrl())
                .siteName(orderRequest.siteName())
                .serviceType(orderRequest.serviceType())
                .targetEngine(orderRequest.targetEngine())
                .analysisItems(String.join("||", orderRequest.normalizedAnalysisItems()))
                .contactName(orderRequest.contactName())
                .contactPhone(orderRequest.contactPhone())
                .contactEmail(orderRequest.contactEmail())
                .contactOrg(orderRequest.contactOrg())
                .memo(orderRequest.memo())
                .domainStatus(orderRequest.resolvedDomainStatus())
                .withImprovement(orderRequest.isImprovementRequested())
                .build();
    }
}
