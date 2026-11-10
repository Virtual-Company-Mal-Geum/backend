package com.malgeum.geo.domain.domain.order.entity;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.time.LocalDate;

import com.malgeum.geo.domain.BaseTimeEntity;
import com.malgeum.geo.domain.domain.client.entity.Client;
import com.malgeum.geo.domain.pricing.account.entity.PricingMode;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "client_order")
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class Order extends BaseTimeEntity { // 백엔드에서 처리하는 사용자의 주문 정보 DTO (FREE/Pack 주문을 모두 포함하는 이 스키마 동일)
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    @Column(name = "target_url", nullable = false, length = 2048)
    private String targetUrl;

    @Column(name = "site_name", nullable = false, length = 255)
    private String siteName;

    @Column(name = "service_type", nullable = false, length = 100)
    private String serviceType;

    @Column(name = "target_engine", length = 100)
    private String targetEngine;

    @Column(name = "analysis_items", columnDefinition = "TEXT")
    private String analysisItems;

    @Column(name = "contact_name", length = 100)
    private String contactName;

    @Column(name = "contact_phone", length = 50)
    private String contactPhone;

    @Column(name = "contact_email", length = 255)
    private String contactEmail;

    @Column(name = "contact_org", length = 255)
    private String contactOrg;

    @Column(name = "memo", columnDefinition = "TEXT")
    private String memo;

    @Enumerated(EnumType.STRING)
    @Column(name = "domain_status", nullable = false)
    private DomainStatus domainStatus;

    @Column(name = "resource_key", nullable = false, length = 36, unique = true)
    private String resourceKey;

    // /diagnose 인지.(개선안(JSON-LD)을 함께 요청한 주문인지.) 재평가는 이 값이 true인 원 주문에만 허용된다.
    @Column(name = "with_improvement", nullable = false, columnDefinition = "boolean not null default false")
    private boolean withImprovement;

    @Enumerated(EnumType.STRING)
    @Column(name = "pricing_mode", length = 20)
    private PricingMode pricingMode;

    // 무료 분석 횟수 카운터를 위한 월 단위 기준일. 매월 1일로 초기화됨.
    @Column(name = "free_scan_monthly_base_date", length = 10)
    private LocalDate freeScanMonthlyBaseDate;

    public void recordPricing(PricingMode mode, LocalDate freeScanCountedMonth) {
        this.pricingMode = mode;
        this.freeScanMonthlyBaseDate = freeScanCountedMonth;
    }

    // 재평가 주문이면 원 주문(최초 평가)을 가리킨다. 재평가끼리 잇지 않고 항상 원 주문을 가리킨다.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_order_id")
    private Order parentOrder;

    @Builder
    public Order(Client client, String targetUrl, String siteName, String serviceType, String targetEngine,
            String analysisItems, String contactName, String contactPhone,
            String contactEmail, String contactOrg, String memo, DomainStatus domainStatus,
            boolean withImprovement) {
        this.client = client;
        this.targetUrl = targetUrl;
        this.siteName = siteName;
        this.serviceType = serviceType;
        this.targetEngine = targetEngine;
        this.analysisItems = analysisItems;
        this.contactName = contactName;
        this.contactPhone = contactPhone;
        this.contactEmail = contactEmail;
        this.contactOrg = contactOrg;
        this.memo = memo;
        this.resourceKey = UUID.randomUUID().toString();
        this.domainStatus = domainStatus;
        this.withImprovement = withImprovement;
    }

    // 원 주문의 URL·도메인을 그대로 복사한 재평가 주문. 클라이언트가 URL을 바꿀 수 없다.
    // 주문 이름은 "원 주문 이름 - 재분석 N". site_name 길이(255)를 넘으면 원래 이름 쪽을 자른다.
    public static Order createDuplicateForReevaluation(Order parentOrder, int sequence) {
        String suffix = " - 재분석 " + sequence;
        String parentOrderName = parentOrder.siteName.length() + suffix.length() > 255
                ? parentOrder.siteName.substring(0, 255 - suffix.length())
                : parentOrder.siteName;
        Order order = new Order(parentOrder.client, parentOrder.targetUrl, parentOrderName + suffix, parentOrder.serviceType,
                parentOrder.targetEngine, parentOrder.analysisItems, parentOrder.contactName, parentOrder.contactPhone,
                parentOrder.contactEmail, parentOrder.contactOrg, parentOrder.memo, parentOrder.domainStatus, false);
        order.parentOrder = parentOrder;
        return order;
    }

    public boolean isReevaluation() {
        return parentOrder != null;
    }

    public List<String> getAnalysisItemList() {
        if (analysisItems == null || analysisItems.isBlank()) {
            return List.of();
        }
        return Arrays.stream(analysisItems.split("\\|\\|"))
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .toList();
    }

    public enum DomainStatus {
        // 뉴스, 이커머스(쇼핑), 교육, 기술 블로그
        NEWS {
            @Override
            public String toString() {
                return "news";
            }
        },
        ECOMMERCE {
            @Override
            public String toString() {
                return "ecommerce";
            }
        },
        EDUCATION {
            @Override
            public String toString() {
                return "education";
            }
        }
    }
}
