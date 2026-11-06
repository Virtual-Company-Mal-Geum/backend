package com.malgeum.geo.domain.pricing.account.entity;

import java.time.LocalDate;
import java.util.Objects;

import org.springframework.http.HttpStatus;

import com.malgeum.geo.domain.domain.client.entity.Client;
import com.malgeum.geo.global.common.ApiException;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "pricing_account")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class PricingAccount { //client의 결제/구독 정보 담당 객체
    public static final int FREE_MONTHLY_SCAN_LIMIT = 5;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", nullable = false, unique = true)
    private Client client;

    @Column(name = "scan_usage_month", nullable = false)
    private LocalDate scanUsageMonth;

    @Column(name = "free_scan_used_count", nullable = false)
    private int freeScanUsedCount;

    public PricingAccount(Client client, LocalDate month) {
        this.client = Objects.requireNonNull(client);
        this.scanUsageMonth = month.withDayOfMonth(1);
    }

    public void acceptGeneralScan(PricingMode mode, LocalDate month) {
        LocalDate normalizedMonth = month.withDayOfMonth(1);
        if (!normalizedMonth.equals(scanUsageMonth)) {
            scanUsageMonth = normalizedMonth;
            freeScanUsedCount = 0;
        }
        if (mode == PricingMode.FREE) {
            if (freeScanUsedCount >= FREE_MONTHLY_SCAN_LIMIT) {
                throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "FREE_SCAN_LIMIT_EXCEEDED",
                        "이번 달 무료 분석 5회를 모두 사용했습니다.");
            }
            freeScanUsedCount++;
        }
    }

    public int freeScansUsedIn(LocalDate month) {
        return month.withDayOfMonth(1).equals(scanUsageMonth) ? freeScanUsedCount : 0;
    }
}
