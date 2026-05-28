package com.malgeum.geo.service;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import com.malgeum.geo.domain.domain.Client;
import com.malgeum.geo.domain.domain.Order;
import com.malgeum.geo.domain.domain.Order.DomainStatus;
import com.malgeum.geo.dto.GeoOrderRequest;
import com.malgeum.geo.global.common.ClientRepository;
import com.malgeum.geo.global.common.OrderRepository;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {
    private final OrderRepository orderRepository;
    private final ClientRepository clientRepository;
    private final GeoAsyncWorker geoAsyncWorker;

    @SuppressWarnings("null")
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

    @SuppressWarnings("null")
    @Transactional
    public Long acceptOrder(GeoOrderRequest orderRequest) {
        String clientIdStr = SecurityContextHolder.getContext().getAuthentication().getName();
        Long clientId = Long.valueOf(clientIdStr);

        log.info("[OrderService] 현재 로그인중인 클라이언트 확인 완료 - ClientID: {}",clientId);

        Client client = clientRepository.findById(clientId)
                .orElseThrow(() -> new IllegalArgumentException("유효하지 않은 고객입니다."));
        Order savedOrder = orderRepository.save(createOrder(client, orderRequest));
        log.info("[OrderService] 새로운 분석 주문 접수 완료 - OrderID: {}", savedOrder.getId());

        geoAsyncWorker.processAnalysis(savedOrder.getId());
        return savedOrder.getId();
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
}
