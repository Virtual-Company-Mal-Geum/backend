package com.malgeum.geo.domain.pricing.partnersubscription.entity;

import com.malgeum.geo.domain.PricingModelTimeEntity;
import com.malgeum.geo.domain.domain.client.entity.Client;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "partner_subscription")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class PartnerSubscription extends PricingModelTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    @Enumerated(EnumType.STRING)
    @Column(name = "tier", nullable = false, length = 20)
    private PartnerTier tier;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SubscriptionStatus status;

    @Column(name = "additional_academy_count", nullable = false)
    private int additionalAcademyCount;

    @Column(name = "monthly_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal monthlyPrice;

    @Column(name = "price_lock_until")
    private LocalDateTime priceLockUntil;

    public enum SubscriptionStatus {
        PENDING_PAYMENT, ACTIVE, CANCELLED
    }

    public enum PartnerTier {
        FOUNDING_PARTNER(10), PARTNER(20), PARTNER_PLUS(50);

        private final int includedAcademies;

        PartnerTier(int includedAcademies) {
            this.includedAcademies = includedAcademies;
        }

        public int getIncludedAcademies() {
            return includedAcademies;
        }
    }

    @Builder
    public PartnerSubscription(Client client, PartnerTier tier, SubscriptionStatus status, int additionalAcademyCount,
                               BigDecimal monthlyPrice) {
        this.client = client;
        this.tier = tier;
        this.status = status;
        this.additionalAcademyCount = additionalAcademyCount;
        this.monthlyPrice = monthlyPrice;
        if (tier == PartnerTier.FOUNDING_PARTNER) {
            this.priceLockUntil = LocalDateTime.now().plusMonths(12);
        }
    }

    @Override
    protected LocalDateTime calculateExpiredAt(LocalDateTime purchasedAt) {
        return purchasedAt.plusMonths(1);
    }

    public int getAcademyCapacity() {
        return tier.getIncludedAcademies() + additionalAcademyCount;
    }

    public void renew() {
        extendExpiredAt(getExpiredAt().plusMonths(1));
    }

    public void cancel() {
        this.status = SubscriptionStatus.CANCELLED;
    }
}
