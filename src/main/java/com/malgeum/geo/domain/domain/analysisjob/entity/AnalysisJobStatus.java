package com.malgeum.geo.domain.domain.analysisjob.entity;

public enum AnalysisJobStatus {
    PENDING,     // 처리 대기
    RUNNING,     // 처리중
    SUCCEEDED,   // 성공
    RETRY_WAIT,  // 재시도 대기
    FAILED;      // 최종 실패

    // 프론트/외부에 노출하는 4단계 상태로 축약
    public ExternalStatus toExternal() {
        return switch (this) {
            case PENDING -> ExternalStatus.ACCEPTED;
            case RUNNING, RETRY_WAIT -> ExternalStatus.PROCESSING;
            case SUCCEEDED -> ExternalStatus.COMPLETED;
            case FAILED -> ExternalStatus.FAILED;
        };
    }

    public enum ExternalStatus {
        ACCEPTED, PROCESSING, COMPLETED, FAILED
    }
}
