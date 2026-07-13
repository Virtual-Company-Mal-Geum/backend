package com.malgeum.geo.domain.domain.order.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import com.malgeum.geo.domain.domain.client.entity.Client;
import com.malgeum.geo.domain.domain.client.repository.ClientRepository;
import com.malgeum.geo.domain.domain.order.entity.Order;
import com.malgeum.geo.domain.domain.order.entity.Order.DomainStatus;
import com.malgeum.geo.domain.domain.order.repository.OrderRepository;
import com.malgeum.geo.dto.GeoOrderRequest;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@DataJpaTest
@Import({ OrderService.class})
public class OrderServiceTest {
    @Autowired
    private OrderService orderService;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Test
    @DisplayName("Client의 Order이 제대로 입력되어 저장되는지 확인한다.")
    void createNewOrderAndSave() {
        String targetUrl = "https://biztoss.co.kr/";
        Optional<Client> savedClient = clientRepository.findById(1L);
        assertThat(savedClient.isPresent());
        GeoOrderRequest orderRequest = GeoOrderRequest.from(targetUrl,"비즈토스","커머스");
        Order newOrder = orderService.createOrder(savedClient.get(),orderRequest);
        // when
        orderRepository.save(newOrder);
        // then
        assertThat(newOrder.getId()).isNotNull();
        assertThat(newOrder.getTargetUrl()).isEqualTo("https://biztoss.co.kr/");
        assertThat(newOrder.getDomainStatus()).isEqualTo(DomainStatus.ECOMMERCE);
        assertThat(newOrder.getResourceKey()).isEqualTo(Client.ClientStatus.ACTIVE);
    }

    @Test
    @DisplayName("현재 로그인한 Client의 Order이 제대로 입력되어 저장되는지 확인한다.")
    void accpetOrder() {
        String targetUrl = "https://biztoss.co.kr/";
        Long orderId = orderService.acceptOrder(targetUrl, DomainStatus.EDUCATION);
        Optional<Order> savedOrder = orderRepository.findById(orderId);
        
        assertThat(savedOrder.isPresent());
        Order newOrder = savedOrder.get();
        assertThat(newOrder.getId()).isNotNull();
        assertThat(newOrder.getClient().getEmail()).isEqualTo("test001@malgeum.com");
        assertThat(newOrder.getOrderStatus()).isEqualTo(Order.OrderStatus.FAILED);
    }
}
