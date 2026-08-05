package com.malgeum.geo.domain.domain.analysisreport.entity;

import java.time.LocalDateTime;
import java.util.Map;

public record ReportResult(
        Long orderId,
        String targetUrl,
        String jobStatus,
        Map<String, Object> aiResult,
        String errorMessage,
        LocalDateTime createdAt) {
}
