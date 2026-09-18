package com.malgeum.geo.domain.domain.analysisreport.entity;

import java.time.LocalDateTime;
import java.util.Map;

public record ReportResult( //브라우저에게 전달하기 위한 AnalysisReport DTO
        Long orderId,
        String targetUrl,
        String jobStatus,
        Map<String, Object> aiResult,
        String errorMessage,
        LocalDateTime createdAt) {
}
