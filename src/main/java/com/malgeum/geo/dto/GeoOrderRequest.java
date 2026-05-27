package com.malgeum.geo.dto;

import java.util.List;

import com.malgeum.geo.domain.domain.Order.CategoryStatus;

public record GeoOrderRequest(
        String targetUrl,
        String siteName,
        String serviceType,
        String targetEngine,
        List<String> analysisItems,
        String contactName,
        String contactPhone,
        String contactEmail,
        String contactOrg,
        String memo) {
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
                null
            );
    }

    public CategoryStatus resolvedCategoryStatus() {
        if (serviceType == null || serviceType.isBlank()) {
            return CategoryStatus.ETC;
        }

        String normalized = serviceType.trim().toLowerCase();
        if (normalized.contains("쇼핑몰") || normalized.contains("이커머스") || normalized.contains("ecommerce")) {
            return CategoryStatus.ECOMMERCE;
        }
        if (normalized.contains("뉴스") || normalized.contains("미디어") || normalized.contains("news")) {
            return CategoryStatus.NEWS;
        }
        if (normalized.contains("교육") || normalized.contains("학술") || normalized.contains("education")) {
            return CategoryStatus.EDUCATION;
        }
        if (normalized.contains("SaaS") || normalized.contains("테크") || normalized.contains("기술")
                || normalized.contains("tech")) {
            return CategoryStatus.TECHBLOG;
        }
        return CategoryStatus.ETC;
    }

    public List<String> normalizedAnalysisItems() {
        return analysisItems == null ? List.of()
                : analysisItems.stream()
                        .filter(item -> item != null && !item.isBlank())
                        .map(String::trim)
                        .toList();
    }
}
