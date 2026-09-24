package com.malgeum.geo.domain.domain.order.entity;

import com.malgeum.geo.domain.BaseTimeEntity;
import com.malgeum.geo.domain.pricing.academy.entity.Academy;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Order 1건이 Partner의 어느 Academy 소속인지 기록하는 조인 엔티티.
 * Order는 그대로 두고(FREE/Pack 주문과 스키마 동일), Partner 플로우에서 생성될 때만 이 행이 추가된다.
 * PartnerSubscription·Client는 Academy를 통해 갈 수 있어 여기 따로 저장하지 않는다(중복 저장 금지).
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
