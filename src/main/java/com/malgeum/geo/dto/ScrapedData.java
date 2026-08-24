package com.malgeum.geo.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

public record ScrapedData(
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
