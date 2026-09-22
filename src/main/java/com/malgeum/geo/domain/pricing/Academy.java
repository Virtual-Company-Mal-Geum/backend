package com.malgeum.geo.domain.pricing;

import com.malgeum.geo.domain.BaseTimeEntity;
import com.malgeum.geo.domain.domain.client.entity.Client;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

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
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "site_url", nullable = false, length = 2048)
    private String siteUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "size_type", nullable = false, length = 20)
    private SizeType sizeType;

    @Column(name = "noise_filter_enabled", nullable = false)
    private boolean noiseFilterEnabled;

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
    public Academy(Client client, String name, String siteUrl, SizeType sizeType) {
        this.client = client;
        this.name = name;
        this.siteUrl = siteUrl;
        this.sizeType = sizeType;
        this.noiseFilterEnabled = sizeType == SizeType.LARGE;
        this.status = AcademyStatus.ACTIVE;
    }

    public void usePage() {
        if (status != AcademyStatus.ACTIVE || monthlyPagesUsed >= MONTHLY_PAGE_QUOTA) {
            throw new IllegalStateException("이번 달 페이지 한도를 초과했거나 해지된 학원입니다.");
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
