package com.malgeum.geo.domain.domain.analysisjob.entity;

public enum AnalysisJobStatus {
    PENDING,     // 처리 대기
    RUNNING,     // AI 서버 요청 중
    SUCCEEDED,   // 성공
    RETRY_WAIT,  // 재시도 대기
    FAILED       // 최종 실패
}
