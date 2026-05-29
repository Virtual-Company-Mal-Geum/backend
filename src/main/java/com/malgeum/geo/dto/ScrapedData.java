package com.malgeum.geo.dto;

import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.NullNode;

public record ScrapedData(
        String url,
        String domain,
        String htmlText,
        JsonNode jsonLd,
        Map<String, String> metaTags) {
    public ScrapedData normalized() {
        return new ScrapedData(
                url,
                domain,
                trimToMax(htmlText, 3500),
                jsonLd == null ? NullNode.getInstance() : jsonLd,
                metaTags == null ? Map.of() : metaTags);
    }

    private static String trimToMax(String text, int maxLength) {
        if (text == null)
            return "";
        return text.length() > maxLength ? text.substring(0, maxLength) : text;
    }
}