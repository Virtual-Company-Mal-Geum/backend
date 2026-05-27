package com.malgeum.geo.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.malgeum.geo.domain.domain.Order;

//Request (Spring -> AI Server)
public record GeoEvaluationRequest(
        @JsonProperty("url") String url,

        @JsonProperty("category") String category,

        @JsonProperty("html_text") String htmlText,

        @JsonProperty("json_ld") String jsonLd,

        @JsonProperty("site_name") String siteName,

        @JsonProperty("service_type") String serviceType,

        @JsonProperty("target_engine") String targetEngine,

        @JsonProperty("analysis_items") List<String> analysisItems) {
    public static GeoEvaluationRequest from(ScrapedData scrapedData) {
        ScrapedData normalized = scrapedData.normalized();
        return new GeoEvaluationRequest(
                normalized.url(),
                normalized.category(),
                normalized.htmlText(),
                normalized.jsonLd(),
                null,
                null,
                null,
                List.of());
    }
    public static GeoEvaluationRequest from(Order order, ScrapedData scrapedData) {
        ScrapedData normalized = scrapedData.normalized();
        return new GeoEvaluationRequest(
                normalized.url(),
                normalized.category(),
                normalized.htmlText(),
                normalized.jsonLd(),
                order.getSiteName(),
                order.getServiceType(),
                order.getTargetEngine(),
                order.getAnalysisItemList());
    }
}
