package com.malgeum.geo.domain;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import lombok.Getter;

import java.time.LocalDateTime;

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
            purchasedAt = LocalDateTime.now();
        }
        if (expiredAt == null) {
            expiredAt = calculateExpiredAt(purchasedAt);
        }
    }

    protected LocalDateTime calculateExpiredAt(LocalDateTime purchasedAt) {
        return purchasedAt.plusDays(365);
    }

    protected void extendExpiredAt(LocalDateTime expiredAt) {
        this.expiredAt = expiredAt;
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiredAt);
    }
}
