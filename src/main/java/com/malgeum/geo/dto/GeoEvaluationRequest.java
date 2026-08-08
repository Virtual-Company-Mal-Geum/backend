package com.malgeum.geo.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

//Request (Spring -> AI Server)
public record GeoEvaluationRequest(
        @JsonProperty("url") String url,

        @JsonProperty("domain") String domain,

        @JsonProperty("html_text") String htmlText,

        @JsonProperty("json_ld") JsonNode jsonLd
    ) {
    public static GeoEvaluationRequest from(ScrapedData scrapedData) {
        ScrapedData normalized = scrapedData.normalized();
        return new GeoEvaluationRequest(
                normalized.url(),
                normalized.domain(),
                normalized.refinedHtmlText(),
                normalized.jsonLd()
            );
    }
}
