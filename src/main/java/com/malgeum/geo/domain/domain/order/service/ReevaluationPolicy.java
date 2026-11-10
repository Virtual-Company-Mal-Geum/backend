package com.malgeum.geo.domain.domain.order.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import com.malgeum.geo.domain.domain.analysisjob.entity.AnalysisJob;
import com.malgeum.geo.domain.domain.analysisjob.entity.AnalysisJobStatus;

// 재평가 횟수·기한 규칙. 주문 접수(OrderService)와 리포트 조회(AnalysisReportService)가 같은 규칙을 쓴다.
public final class ReevaluationPolicy {
    public static final int LIMIT = 2;
    public static final Duration WINDOW = Duration.ofDays(30);

    private ReevaluationPolicy() {
    }

    //재분석이 성공 완료된 경우에만 사용 횟수 차감
    public static int calculateRemainingReevaluationCount(List<AnalysisJob> reevaluationJobs) {
        long used = reevaluationJobs.stream()
                .filter(job -> job.getStatus() != AnalysisJobStatus.FAILED)
                .count();
        return (int) Math.max(0, LIMIT - used);
    }

    // 원 주문 완료 시각 + 30일. completedAt 컬럼 추가 전에 성공한 주문은 마지막 수정 시각으로 대신한다.
    public static LocalDateTime expiresAt(AnalysisJob baselineJob) {
        LocalDateTime completedAt = baselineJob.getCompletedAt() != null
                ? baselineJob.getCompletedAt()
                : baselineJob.getUpdatedAt();
        return completedAt == null ? null : completedAt.plus(WINDOW);
    }
}
