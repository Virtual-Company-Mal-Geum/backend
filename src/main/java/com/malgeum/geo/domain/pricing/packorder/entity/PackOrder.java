package com.malgeum.geo.domain.pricing.packorder.entity;

import com.malgeum.geo.domain.PricingModelTimeEntity;
import com.malgeum.geo.domain.pricing.account.entity.PricingAccount;
import com.malgeum.geo.domain.pricing.account.PricingConfig;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "pack_order")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class PackOrder extends PricingModelTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pricing_account_id", nullable = false)
    private PricingAccount pricingAccount;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private PackType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PackStatus status;

    @Column(name = "total_pages", nullable = false)
    private int totalPages;

    @Column(name = "remaining_pages", nullable = false)
    private int remainingPages;

    @Column(name = "price_paid", nullable = false, precision = 10, scale = 2)
    private BigDecimal pricePaid;

    public enum PackStatus {
        PENDING_PAYMENT, PAID, USED_UP, EXPIRED
    }

    public enum PackType {
        PACK_1(1), PACK_5(5), PACK_20(20);

        private final int pages;

        PackType(int pages) {
            this.pages = pages;
        }

        public int getPages() {
            return pages;
        }
    }

    @Builder
    public PackOrder(PricingAccount pricingAccount, PackType type, PackStatus status, BigDecimal pricePaid) {
        this.pricingAccount = pricingAccount;
        this.type = type;
        this.status = status;
        this.totalPages = type.getPages();
        this.remainingPages = type.getPages();
        this.pricePaid = pricePaid;
    }

    public void usePage() {
        usePage(LocalDateTime.now(PricingConfig.BILLING_ZONE));
    }

    public void usePage(LocalDateTime now) {
        if (status != PackStatus.PAID || remainingPages <= 0 || !getExpiredAt().isAfter(now)) {
            throw new IllegalStateException("사용할 수 없는 Pack입니다.");
        }
        remainingPages--;
        if (remainingPages <= 0) {
            status = PackStatus.USED_UP;
        }
    }
}
