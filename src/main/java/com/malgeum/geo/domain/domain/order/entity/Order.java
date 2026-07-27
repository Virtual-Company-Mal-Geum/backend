package com.malgeum.geo.domain.domain.order.entity;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import com.malgeum.geo.domain.BaseTimeEntity;
import com.malgeum.geo.domain.domain.client.entity.Client;

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
public class Order extends BaseTimeEntity {
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

    @Enumerated(EnumType.STRING)
    @Column(name = "order_status", nullable = false, length = 20)
    private OrderStatus orderStatus;

    @Builder
    public Order(Client client, String targetUrl, String siteName, String serviceType, String targetEngine,
            String analysisItems, String contactName, String contactPhone,
            String contactEmail, String contactOrg, String memo, DomainStatus domainStatus) {
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
        this.orderStatus = OrderStatus.ACCEPTED;
        this.domainStatus = domainStatus;
    }

    public void updateStatus(OrderStatus newStatus) {
        this.orderStatus = newStatus;
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

    public enum OrderStatus {
        ACCEPTED, // 주문 접수 및 작업 등록 완료
        PROCESSING, // 크롤링·AI 분석·재시도 등을 포함해 처리 중
        COMPLETED, // 분석 리포트 생성 완료
        FAILED // 더 이상 처리할 수 없는 최종 실패
    }

    public void markProcessing() {
        this.orderStatus = OrderStatus.PROCESSING;
    }

    public void markCompleted() {
        this.orderStatus = OrderStatus.COMPLETED;
    }

    public void markFailed() {
        this.orderStatus = OrderStatus.FAILED;
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
        },
        TECHBLOG {
            @Override
            public String toString() {
                return "tech_blog";
            }
        }
    }
}
