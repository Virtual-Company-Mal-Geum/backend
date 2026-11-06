package com.malgeum.geo.domain;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import lombok.Getter;

import java.time.LocalDateTime;

import com.malgeum.geo.domain.pricing.account.PricingConfig;

@Getter
@MappedSuperclass
public abstract class PricingModelTimeEntity {
    @Column(name = "purchased_at", nullable = false, updatable = false)
    private LocalDateTime purchasedAt;

    @Column(name = "expired_at", nullable = false)
    private LocalDateTime expiredAt;

    @PrePersist
    protected void initPurchasePeriod() {
        if (purchasedAt == null) {
            purchasedAt = LocalDateTime.now(PricingConfig.BILLING_ZONE);
        }
        if (expiredAt == null) {
            expiredAt = createExpiredAt(purchasedAt);
        }
    }

    protected LocalDateTime createExpiredAt(LocalDateTime purchasedAt) {
        return purchasedAt.plusDays(365);
    }

    protected void changeExpiredAt(LocalDateTime expiredAt) {
        this.expiredAt = expiredAt;
    }

    public boolean isExpired() {
        return !expiredAt.isAfter(LocalDateTime.now(PricingConfig.BILLING_ZONE));
    }
}
