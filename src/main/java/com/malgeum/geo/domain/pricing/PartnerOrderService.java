package com.malgeum.geo.domain.pricing;

import java.time.LocalDateTime;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.malgeum.geo.domain.domain.order.dto.GeoOrderRequest;
import com.malgeum.geo.domain.domain.order.entity.Order;
import com.malgeum.geo.domain.domain.order.entity.PartnerOrder;
import com.malgeum.geo.domain.domain.order.repository.PartnerOrderRepository;
import com.malgeum.geo.domain.domain.order.service.OrderService;
import com.malgeum.geo.domain.pricing.academy.entity.Academy;
import com.malgeum.geo.domain.pricing.academy.repository.AcademyRepository;
import com.malgeum.geo.domain.pricing.partnersubscription.entity.PartnerSubscription;
import com.malgeum.geo.domain.pricing.partnersubscription.repository.PartnerSubscriptionRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PartnerOrderService {
    private final PartnerSubscriptionRepository partnerSubscriptionRepository;
    private final AcademyRepository academyRepository;
    private final PartnerOrderRepository partnerOrderRepository;
    private final OrderService orderService;

    @Transactional
    public Long acceptPartnerOrder(GeoOrderRequest orderRequest) {
        Long clientId = Long.valueOf(SecurityContextHolder.getContext().getAuthentication().getName());

        partnerSubscriptionRepository
                .findByClientIdAndStatusAndExpiredAtAfter(
                        clientId, PartnerSubscription.SubscriptionStatus.ACTIVE, LocalDateTime.now())
                .orElseThrow(() -> new IllegalStateException("활성화된 Partner 구독이 없습니다."));

        String domain = Academy.normalizeDomain(orderRequest.targetUrl());
        Academy academy = academyRepository
                .findByPartnerSubscription_Client_IdAndDomainAndStatus(clientId, domain, Academy.AcademyStatus.ACTIVE)
                .orElseThrow(() -> new IllegalStateException("등록되지 않은 학원 도메인입니다: " + domain));

        academy.usePage();

        Long orderId = orderService.acceptOrder(orderRequest);
        Order order = orderService.getOrder(orderId);
        partnerOrderRepository.save(PartnerOrder.builder().order(order).academy(academy).build());

        return orderId;
    }
}
