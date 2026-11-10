package com.malgeum.geo.domain.domain.order.dto;

import java.util.List;

import com.malgeum.geo.domain.domain.order.entity.Order.DomainStatus;
import com.malgeum.geo.global.common.DataNotFoundException;
import com.malgeum.geo.domain.pricing.account.entity.PricingMode;

public record GeoOrderRequest( // 사용자가 GEO 분석을 요청할 때 브라우저에서 보내는 요청 DTO
                               String targetUrl,
                               String siteName,
                               String serviceType,
                               String targetEngine,
                               List<String> analysisItems,
                               String contactName,
                               String contactPhone,
                               String contactEmail,
                               String contactOrg,
                               String memo,
                               Boolean withImprovement, // 개선안(JSON-LD) 함께 요청 할지말지
                               Long parentOrderId, // 값이 있으면 이 요청의 본 주문은 재평가 요청이란 뜻. 나머지 필드는 무시하고 원 주문에서 복사
                               PricingMode improvementSource) {
    public GeoOrderRequest(String targetUrl, String siteName, String serviceType, String targetEngine,
                           List<String> analysisItems, String contactName, String contactPhone, String contactEmail,
                           String contactOrg, String memo, Boolean withImprovement, Long parentOrderId) {
        this(targetUrl, siteName, serviceType, targetEngine, analysisItems, contactName, contactPhone,
                contactEmail, contactOrg, memo, withImprovement, parentOrderId, null);
    }

    public static GeoOrderRequest from(String url, String siteName, String serviceType) {
        return new GeoOrderRequest(
                url,
                siteName,
                serviceType,
                null,
                List.of(),
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    public boolean isImprovementRequested() {
        return Boolean.TRUE.equals(withImprovement);
    }

    public DomainStatus resolvedDomainStatus() {
        String normalized = serviceType.trim().toLowerCase();
        if (normalized.contains("쇼핑몰") || normalized.contains("이커머스") || normalized.contains("ecommerce")) {
            return DomainStatus.ECOMMERCE;
        }
        if (normalized.contains("뉴스") || normalized.contains("미디어") || normalized.contains("news")) {
            return DomainStatus.NEWS;
        }
        if (normalized.contains("교육") || normalized.contains("학술") || normalized.contains("education")) {
            return DomainStatus.EDUCATION;
        }
        throw new DataNotFoundException("[GeoOrderRequest] Invalid DomainStatus inserted.");
    }

    public List<String> normalizedAnalysisItems() {
        return analysisItems == null ? List.of()
                : analysisItems.stream()
                .filter(item -> item != null && !item.isBlank())
                .map(String::trim)
                .toList();
    }
}
