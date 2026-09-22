package com.malgeum.geo.domain.pricing.packorder.entity;

import com.malgeum.geo.domain.PricingModelTimeEntity;
import com.malgeum.geo.domain.domain.client.entity.Client;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "pack_order")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class PackOrder extends PricingModelTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

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
    public PackOrder(Client client, PackType type, PackStatus status, BigDecimal pricePaid) {
        this.client = client;
        this.type = type;
        this.status = status;
        this.totalPages = type.getPages();
        this.remainingPages = type.getPages();
        this.pricePaid = pricePaid;
    }

    public void usePage() {
        if (status != PackStatus.PAID || isExpired()) {
            throw new IllegalStateException("사용할 수 없는 Pack입니다.");
        }
        remainingPages--;
        if (remainingPages <= 0) {
            status = PackStatus.USED_UP;
        }
    }
}
