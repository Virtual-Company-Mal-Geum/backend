package com.malgeum.geo.serivce;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import com.malgeum.geo.domain.domain.Client;
import com.malgeum.geo.domain.domain.Order;
import com.malgeum.geo.domain.domain.Order.CategoryStatus;
import com.malgeum.geo.global.common.ClientRepository;
import com.malgeum.geo.global.common.OrderRepository;
import com.malgeum.geo.service.OrderService;

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
        Order newOrder = orderService.createOrder(savedClient.get(), targetUrl, Order.CategoryStatus.ECOMMERCE);
        // when
        orderRepository.save(newOrder);
        // then
        assertThat(newOrder.getId()).isNotNull();
        assertThat(newOrder.getTargetUrl()).isEqualTo("https://biztoss.co.kr/");
        assertThat(newOrder.getCategoryStatus()).isEqualTo(CategoryStatus.ECOMMERCE);
        assertThat(newOrder.getResourceKey()).isEqualTo(Client.ClientStatus.ACTIVE);
    }

    @Test
    @DisplayName("현재 로그인한 Client의 Order이 제대로 입력되어 저장되는지 확인한다.")
    void accpetOrder() {
        String targetUrl = "https://biztoss.co.kr/";
        Long orderId = orderService.acceptOrder(targetUrl, CategoryStatus.EDUCATION);
        Optional<Order> savedOrder = orderRepository.findById(orderId);
        
        assertThat(savedOrder.isPresent());
        Order newOrder = savedOrder.get();
        assertThat(newOrder.getId()).isNotNull();
        assertThat(newOrder.getClient().getEmail()).isEqualTo("test001@malgeum.com");
        assertThat(newOrder.getJobStatus()).isEqualTo(Order.JobStatus.FAILED);
    }
}
