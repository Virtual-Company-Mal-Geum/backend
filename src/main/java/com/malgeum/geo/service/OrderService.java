package com.malgeum.geo.service;

import java.net.URL;

import org.springframework.stereotype.Service;

import com.malgeum.geo.domain.domain.Client;
import com.malgeum.geo.domain.domain.Order;
import com.malgeum.geo.global.common.OrderRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {
    private final GeoAiService geoAiService;
    private final GeoScrapingService geoScrapingService;
    private final OrderRepository orderRepository;

    public void create(Client client, URL targetUrl){
        Order order = Order.builder()
                            .client(client)
                            .targetUrl(targetUrl)
                            .build();
        orderRepository.save(order);
    }

    public String processOrder(String orderId) {
        log.info("[OrderService] 주문 처리 시작. 주문 ID: {}", orderId);
        return "주문 " + orderId + "이 성공적으로 처리되었습니다.";
    }

    
}
