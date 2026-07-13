package com.malgeum.geo.domain.domain.analysisjob.entity;

import java.time.LocalDateTime;

import com.malgeum.geo.domain.BaseTimeEntity;
import com.malgeum.geo.domain.domain.order.entity.Order;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "analysis_job")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class AnalysisJob extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false, unique = true)
    private Order order;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    AnalysisJobStatus status;

    @Column(name = "attempts", nullable = false)
    int attempts;

    @Column(name = "max_attempts", nullable = false)
    int maxAttempts;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "TEXT")
    private FailureStage lastFailureStage;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    @Column(nullable = false)
    private LocalDateTime nextRunAt;

    private LocalDateTime lockedAt;

    public static AnalysisJob create(Order order) {
        AnalysisJob job = new AnalysisJob();
        job.order = order;
        job.status = AnalysisJobStatus.PENDING;
        job.attempts = 0;
        job.maxAttempts = 3;
        job.nextRunAt = LocalDateTime.now();
        return job;
    }

    public void markRunning() {
        this.status = AnalysisJobStatus.RUNNING;
        this.attempts += 1;
        this.lockedAt = LocalDateTime.now();
    }

    public void markSucceeded() {
        this.status = AnalysisJobStatus.SUCCEEDED;
        this.errorMessage = null;
        this.lockedAt = null;
    }

    public void markRetryWait(String errorMessage, LocalDateTime nextRunAt) {
        this.status = AnalysisJobStatus.RETRY_WAIT;
        this.errorMessage = errorMessage;
        this.nextRunAt = nextRunAt;
        this.lockedAt = null;
    }

    public void markFailed(String errorMessage) {
        this.status = AnalysisJobStatus.FAILED;
        this.errorMessage = errorMessage;
        //TODO: this.lastFailureStage = checkFailureStage(errorMessage);
        this.lockedAt = null;
    }

    public boolean canRetry() {
        return this.attempts < this.maxAttempts;
    }

    // 실패 지점 표시
    public enum FailureStage {
        ORDER_LOADING,
        SCRAPING,
        AI_REQUEST,
        AI_RESPONSE_PARSING,
        REPORT_SAVING
    }

    private FailureStage checkFailureStage(String errorMessage){
        //TODO: errorMsg를 읽고 어떤 실패지점인지 분기 만들기
        return FailureStage.REPORT_SAVING;
    }
}
