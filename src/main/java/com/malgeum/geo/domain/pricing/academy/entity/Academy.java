package com.malgeum.geo.domain.pricing.academy.entity;

import com.malgeum.geo.domain.BaseTimeEntity;
import com.malgeum.geo.domain.pricing.partnersubscription.entity.PartnerSubscription;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.net.URI;

@Entity
@Table(name = "academy")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class Academy extends BaseTimeEntity {
    public static final int MONTHLY_PAGE_QUOTA = 10;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "partner_subscription_id", nullable = false)
    private PartnerSubscription partnerSubscription;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "domain", nullable = false, length = 255)
    private String domain;

    @Enumerated(EnumType.STRING)
    @Column(name = "size_type", nullable = false, length = 20)
    private SizeType sizeType;

    @Column(name = "noise_filter_enabled", nullable = false)
    private boolean noiseFilterEnabled; //소형학원의 경우, 노이즈 데이터로 판단되던 광고창에서 마저도 자기 PR(광고)을 하는 경우가 존재함

    @Column(name = "monthly_pages_used", nullable = false)
    private int monthlyPagesUsed;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AcademyStatus status;

    public enum SizeType {
        LARGE, SMALL
    }

    public enum AcademyStatus {
        ACTIVE, REMOVED
    }

    @Builder
    public Academy(PartnerSubscription partnerSubscription, String name, String domain, SizeType sizeType) {
        this.partnerSubscription = partnerSubscription;
        this.name = name;
        this.domain = normalizeDomain(domain);
        this.sizeType = sizeType;
        this.noiseFilterEnabled = sizeType == SizeType.LARGE;
        this.status = AcademyStatus.ACTIVE;
    }

    /**
     * "www.naver.com" 형태로 통일. 전체 URL이 들어오면 host만 뽑고, www.는 떼어 비교 기준을 하나로 맞춘다.
     */
    public static String normalizeDomain(String raw) {
        String value = raw.trim();
        if (value.contains("://")) {
            value = URI.create(value).getHost();
        }
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("유효하지 않은 도메인입니다: " + raw);
        }
        value = value.toLowerCase();
        return value.startsWith("www.") ? value.substring(4) : value;
    }

    public void usePage() {
        if (status != AcademyStatus.ACTIVE) {
            throw new IllegalStateException("학원이 활성화 상태가 아닙니다.");
        }
        if (monthlyPagesUsed >= MONTHLY_PAGE_QUOTA) {
            throw new IllegalStateException("이번 달 페이지 한도를 초과한 학원입니다. (학원당 " + MONTHLY_PAGE_QUOTA + "개 페이지)");
        }
        monthlyPagesUsed++;
    }

    public void resetMonthlyUsage() {
        this.monthlyPagesUsed = 0;
    }

    public void setNoiseFilterEnabled(boolean enabled) {
        this.noiseFilterEnabled = enabled;
    }

    public void remove() {
        this.status = AcademyStatus.REMOVED;
    }
}
