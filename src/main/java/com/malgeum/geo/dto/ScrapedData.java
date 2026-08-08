package com.malgeum.geo.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.NullNode;

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
                jsonLd == null ? NullNode.getInstance() : jsonLd);
    }

    private static String trimToMax(String text, int maxLength) {
        if (text == null)
            return "";
        return text.length() > maxLength ? text.substring(0, maxLength) : text;
    }
}