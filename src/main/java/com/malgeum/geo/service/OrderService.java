package com.malgeum.geo.service;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import com.malgeum.geo.domain.domain.Client;
import com.malgeum.geo.domain.domain.Order;
import com.malgeum.geo.domain.domain.Order.CategoryStatus;
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
    public Long acceptOrder(String targetUrl,CategoryStatus categoryStatus) {
        // 1. JWT 필터를 통과한 현재 로그인 고객사 ID 가져오기
        String clientIdStr = SecurityContextHolder.getContext().getAuthentication().getName();
        Long clientId = Long.valueOf(clientIdStr);

        Client client = clientRepository.findById(clientId)
                .orElseThrow(() -> new IllegalArgumentException("유효하지 않은 고객사입니다."));
        Order savedOrder = orderRepository.save(createOrder(client, targetUrl, categoryStatus));
        log.info("[OrderService] 새로운 분석 주문 접수 완료 - OrderID: {}", savedOrder.getId());

        // 3. ⭐️ 핵심: 비동기 워커에게 "이 주문번호(orderId)랑 URL 가지고 가서 일해!" 라고 던짐
        // 이 메서드는 호출 즉시 리턴되며, 실제 작업은 다른 스레드에서 돌아갑니다.
        geoAsyncWorker.processAnalysis(savedOrder.getId());
        return savedOrder.getId();
    }

    // 2. 주문서 생성 (초기 상태: PENDING)
    public Order createOrder(Client client, String targetUrl,CategoryStatus categoryStatus) {
        Order order = Order.builder()
                .client(client)
                .targetUrl(targetUrl)
                .categoryStatus(categoryStatus)
                .build();
        return order;
    }

}
