package com.malgeum.geo.dto;

import java.time.LocalDateTime;
import java.util.Map;

public record ReportResult(
        Long orderId,
        String targetUrl,
        String jobStatus,
        Map<String, Object> aiResult,
        LocalDateTime createdAt) {
}
