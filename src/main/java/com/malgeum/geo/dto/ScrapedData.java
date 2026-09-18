package com.malgeum.geo.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

public record ScrapedData( //백엔드에서만 사용되는 크롤링 결과 DTO
        String url,
        String domain,
        String refinedHtmlText,
        JsonNode jsonLd) {
    public ScrapedData normalized() {
        return new ScrapedData(
                url,
                domain,
                refinedHtmlText,
                jsonLd == null ? JsonNodeFactory.instance.arrayNode() : jsonLd);
    }
}
