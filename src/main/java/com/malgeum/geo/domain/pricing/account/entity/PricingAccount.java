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

    // 무료 분석 횟수 카운터를 위한 월 단위 기준일. 매월 1일로 초기화됨.
    @Column(name = "free_scan_monthly_base_date", nullable = false)
    private LocalDate freeScanMonthlyBaseDate;

    // 이번 달에 사용한 무료 분석 횟수
    @Column(name = "free_scan_used_count", nullable = false)
    private int freeScanUsedCount;

    public PricingAccount(Client client, LocalDate month) {
        this.client = Objects.requireNonNull(client);
        this.freeScanMonthlyBaseDate = month.withDayOfMonth(1);
    }

    public void acceptGeneralScan(PricingMode mode, LocalDate month) {
        LocalDate normalizedMonth = month.withDayOfMonth(1);
        if (!normalizedMonth.equals(freeScanMonthlyBaseDate)) {
            freeScanMonthlyBaseDate = normalizedMonth;
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
        return month.withDayOfMonth(1).equals(freeScanMonthlyBaseDate) ? freeScanUsedCount : 0;
    }
}
