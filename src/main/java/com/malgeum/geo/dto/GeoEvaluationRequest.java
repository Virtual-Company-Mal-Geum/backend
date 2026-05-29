package com.malgeum.geo.dto;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

//Request (Spring -> AI Server)
public record GeoEvaluationRequest(
        @JsonProperty("url") String url,

        @JsonProperty("domain") String domain,

        @JsonProperty("html_text") String htmlText,

        @JsonProperty("json_ld") String jsonLd,

        @JsonProperty("meta_tags") Map<String,String> metaTags
    ) {
    public static GeoEvaluationRequest from(ScrapedData scrapedData) {
        ScrapedData normalized = scrapedData.normalized();
        return new GeoEvaluationRequest(
                normalized.url(),
                normalized.domain(),
                normalized.htmlText(),
                normalized.jsonLd(),
                normalized.metaTags()
            );
    }
}
