package com.malgeum.geo.domain.domain.order.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import com.malgeum.geo.domain.domain.analysisjob.service.AnalysisJobService;
import com.malgeum.geo.domain.domain.analysisjob.entity.AnalysisJob;
import com.malgeum.geo.domain.domain.analysisjob.repository.AnalysisJobRepository;
import com.malgeum.geo.domain.pricing.account.PricingConfig;
import com.malgeum.geo.domain.pricing.account.service.PricingService;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import com.malgeum.geo.domain.domain.client.entity.Client;
import com.malgeum.geo.domain.domain.client.repository.ClientRepository;
import com.malgeum.geo.domain.domain.order.dto.GeoOrderRequest;
import com.malgeum.geo.domain.domain.order.entity.Order;
import com.malgeum.geo.domain.domain.order.entity.Order.DomainStatus;
import com.malgeum.geo.domain.domain.order.repository.OrderRepository;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@DataJpaTest(properties = "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect", showSql = false)
@Import({ OrderService.class, AnalysisJobService.class, PricingService.class, PricingConfig.class })
public class OrderServiceTest {
    @Autowired
    private OrderService orderService;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private AnalysisJobRepository analysisJobRepository;

    @Autowired
    private PricingService pricingService;

    private Client client;

    @BeforeEach
    void createMember() {
        client = clientRepository.save(Client.builder().name("test").email(UUID.randomUUID() + "@example.com").build());
        pricingService.createAccount(client);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(client.getId().toString(), "", List.of()));
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Client의 Order이 제대로 입력되어 저장되는지 확인한다.")
    void createNewOrderAndSave() {
        String targetUrl = "https://biztoss.co.kr/";
        GeoOrderRequest orderRequest = GeoOrderRequest.from(targetUrl,"비즈토스","이커머스");
        Order newOrder = orderService.createOrder(client,orderRequest);
        // when
        orderRepository.save(newOrder);
        // then
        assertThat(newOrder.getId()).isNotNull();
        assertThat(newOrder.getTargetUrl()).isEqualTo("https://biztoss.co.kr/");
        assertThat(newOrder.getDomainStatus()).isEqualTo(DomainStatus.ECOMMERCE);
        assertThat(UUID.fromString(newOrder.getResourceKey())).isNotNull();
    }

    @Test
    @DisplayName("현재 로그인한 Client의 Order이 제대로 입력되어 저장되는지 확인한다.")
    void acceptOrder() {
        String targetUrl = "https://biztoss.co.kr/";
        Long orderId = orderService.acceptOrder(new GeoOrderRequest(
                targetUrl,
                "test-site",
                DomainStatus.ECOMMERCE.toString(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null));
        Optional<Order> savedOrder = orderRepository.findById(orderId);
        
        assertThat(savedOrder).isPresent();
        Order newOrder = savedOrder.get();
        assertThat(newOrder.getId()).isNotNull();
        assertThat(newOrder.getClient().getId()).isEqualTo(client.getId());
    }

    @Test
    @DisplayName("parentOrderId가 있는 경우, 재평가 주문이 생성되는지 확인한다.")
    void acceptOrderWithParentOrderIdCreatesReevaluationOrder() {
        Order parentOrder = orderRepository.save(Order.builder()
                .client(client)
                .targetUrl("https://parent-site.com/")
                .siteName("parent-site")
                .serviceType(DomainStatus.ECOMMERCE.toString())
                .domainStatus(DomainStatus.ECOMMERCE)
                .withImprovement(true)
                .build());
        AnalysisJob parentJob = AnalysisJob.create(parentOrder);
        parentJob.markSucceeded();
        analysisJobRepository.save(parentJob);

        Long orderId = orderService.acceptOrder(new GeoOrderRequest(
                "https://ignored-by-parent-order-id.com/",
                "ignored-site-name",
                DomainStatus.NEWS.toString(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                parentOrder.getId()));
        Optional<Order> savedOrder = orderRepository.findById(orderId);

        assertThat(savedOrder).isPresent();
        Order reevaluationOrder = savedOrder.get();
        assertThat(reevaluationOrder.getId()).isNotNull();
        assertThat(reevaluationOrder.getClient().getId()).isEqualTo(client.getId());
        assertThat(reevaluationOrder.getParentOrder().getId()).isEqualTo(parentOrder.getId());
        assertThat(reevaluationOrder.getTargetUrl()).isEqualTo(parentOrder.getTargetUrl());
        assertThat(reevaluationOrder.getSiteName()).isEqualTo("parent-site - 재분석 1");
        assertThat(analysisJobRepository.findByOrderId(reevaluationOrder.getId())).isPresent();
    }
}
