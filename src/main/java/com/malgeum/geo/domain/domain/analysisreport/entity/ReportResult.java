package com.malgeum.geo.domain.domain.analysisreport.entity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import com.malgeum.geo.domain.domain.analysisjob.entity.AnalysisJob;
import com.malgeum.geo.domain.domain.analysisjob.entity.AnalysisJobStatus;

public record ReportResult( //브라우저에게 전달하기 위한 AnalysisReport DTO
                            Long orderId,
                            String siteName, // 재평가 주문이면 "원 주문 이름 - 재분석 N"
                            String targetUrl,
                            String jobStatus,
                            Map<String, Object> aiResult,
                            String errorMessage,
                            LocalDateTime createdAt,
                            // 개선 전후 비교용. 재평가 주문이면 원 주문 id, 원 주문이면 null
                            Long parentOrderId,
                            // 원 주문이 첫 번째, 그 뒤로 재평가 주문이 생성 순서대로
                            List<Version> versions,
                            // 개선안 없이 요청한 주문(재평가 대상 아님)이면 null
                            Integer reevalRemaining,
                            LocalDateTime reevalExpiresAt) {

    public record Version(Long orderId, String siteName, String jobStatus, LocalDateTime createdAt, Score score) {
    }

    public record Score(Double raw, Double total) {
        // 완료된 주문의 AI 결과에서 raw_score·total_score를 꺼낸다.
        // /diagnose(개선안 포함)는 analysis, /evaluate는 result 키에 채점 결과가 있다.
        public static Score from(AnalysisJob job, Map<String, Object> aiLog) {
            if (job == null || job.getStatus() != AnalysisJobStatus.SUCCEEDED || aiLog == null) {
                return null;
            }
            Object evaluation = aiLog.containsKey("analysis") ? aiLog.get("analysis") : aiLog.get("result");
            if (!(evaluation instanceof Map<?, ?> map)) {
                return null;
            }
            return new Score(toDouble(map.get("raw_score")), toDouble(map.get("total_score")));
        }

        private static Double toDouble(Object value) {
            return value instanceof Number number ? number.doubleValue() : null;
        }
    }
}
