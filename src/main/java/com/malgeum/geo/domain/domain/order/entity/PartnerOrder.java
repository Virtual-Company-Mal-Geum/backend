package com.malgeum.geo.domain.domain.order.entity;

import com.malgeum.geo.domain.BaseTimeEntity;
import com.malgeum.geo.domain.pricing.academy.entity.Academy;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Order 1건이 Partner의 어느 Academy 소속인지 기록하는 조인 엔티티.
 * Order (FREE/Pack 주문과 스키마 동일)와 달리 Partner 구독 유저 전용 Order
 */
@Entity
@Table(name = "partner_order")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class PartnerOrder extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false, unique = true)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "academy_id", nullable = false)
    private Academy academy;

    @Builder
    public PartnerOrder(Order order, Academy academy) {
        this.order = order;
        this.academy = academy;
    }
}
