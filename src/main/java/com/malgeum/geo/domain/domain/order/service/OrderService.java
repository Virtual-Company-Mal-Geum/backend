package com.malgeum.geo.domain.domain.order.service;

import java.time.LocalDateTime;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.malgeum.geo.domain.domain.client.entity.Client;
import com.malgeum.geo.domain.domain.client.repository.ClientRepository;
import com.malgeum.geo.domain.domain.analysisjob.service.AnalysisJobService;
import com.malgeum.geo.domain.domain.order.dto.GeoOrderRequest;
import com.malgeum.geo.domain.domain.order.entity.Order;
import com.malgeum.geo.domain.domain.order.entity.Order.DomainStatus;
import com.malgeum.geo.domain.domain.order.repository.OrderRepository;
import com.malgeum.geo.global.common.DataNotFoundException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {
    private final OrderRepository orderRepository;
    private final ClientRepository clientRepository;
    private final AnalysisJobService analysisJobService;

    @Transactional
    public Long acceptOrder(String targetUrl, DomainStatus domainStatus) {
        return acceptOrder(new GeoOrderRequest(
                targetUrl,
                null,
                domainStatus == null ? null : domainStatus.toString(),
                null,
                null,
                null,
                null,
                null,
                null,
                null));
    }

    @Transactional
    public Long acceptOrder(GeoOrderRequest orderRequest) {
        Order savedOrder = saveOrder(orderRequest);
        analysisJobService.enqueue(savedOrder);
        return savedOrder.getId();
    }

    private Order saveOrder(GeoOrderRequest orderRequest) {
        String clientIdStr = SecurityContextHolder.getContext().getAuthentication().getName();
        Long clientId = Long.valueOf(clientIdStr);

        log.info("[OrderService] 현재 로그인중인 클라이언트 확인 완료 - ClientID: {}", clientId);

        Client client = clientRepository.findById(clientId)
                .orElseThrow(() -> new IllegalArgumentException("유효하지 않은 고객입니다."));
        Order savedOrder = orderRepository.save(createOrder(client, orderRequest));
        log.info("[OrderService] 새로운 분석 주문 접수 완료 - OrderID: {}", savedOrder.getId());

        return savedOrder;
    }
    
    @Transactional(readOnly = true)
    public Order getOrder(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new DataNotFoundException("Order not found. id=" + orderId));
    }

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
                .build();
    }

    public record OrderSummaryResponse(
            Long orderId,
            String siteName,
            String targetUrl,
            String domainStatus,
            String jobStatus,
            LocalDateTime createdAt) {
        public static OrderSummaryResponse from(Order order) {
            return new OrderSummaryResponse(
                    order.getId(),
                    order.getSiteName(),
                    order.getTargetUrl(),
                    order.getDomainStatus().name(),
                    order.getOrderStatus().name(),
                    order.getCreatedAt());
        }
    }
}
