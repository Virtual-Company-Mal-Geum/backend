package com.malgeum.geo.dto;

import java.util.Set;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

//Request (Spring -> AI Server)
public record GeoEvaluationRequest(
        @JsonProperty("url") String url,

        @JsonProperty("domain") String domain,

        @JsonProperty("html_text") String htmlText,

        @JsonProperty("json_ld") JsonNode jsonLd
    ) {
    // AI 서버(geo_gateway.py)의 SUPPORTED_DOMAINS와 동일 — tech_blog는 학습 데이터 부족으로 서빙되지 않는다.
    private static final Set<String> SUPPORTED_DOMAINS = Set.of("news", "ecommerce", "education");

    public static GeoEvaluationRequest from(ScrapedData scrapedData) {
        ScrapedData normalized = scrapedData.normalized();
        if (!SUPPORTED_DOMAINS.contains(normalized.domain())) {
            throw new IllegalStateException(
                    "AI 서버가 지원하지 않는 도메인입니다: " + normalized.domain());
        }
        return new GeoEvaluationRequest(
                normalized.url(),
                normalized.domain(),
                normalized.refinedHtmlText(),
                normalized.jsonLd()
            );
    }
}
