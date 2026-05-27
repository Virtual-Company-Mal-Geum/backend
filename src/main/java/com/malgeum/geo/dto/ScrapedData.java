package com.malgeum.geo.dto;

public record ScrapedData(
        String url,
        String category,
        String htmlText,
        String jsonLd) {
    public ScrapedData normalized() {
        return new ScrapedData(
                url,
                category,
                trimToMax(htmlText, 8190),
                jsonLd == null ? "" : jsonLd);
    }

    private static String trimToMax(String text, int maxLength) {
        if (text == null)
            return "";
        return text.length() > maxLength ? text.substring(0, maxLength) : text;
    }
}