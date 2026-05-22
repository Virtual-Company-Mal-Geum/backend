package com.malgeum.geo.domain.domain;

import java.util.Map;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.malgeum.geo.domain.BaseTimeEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "analysis_report")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class AnalysisReport extends BaseTimeEntity {
    @Id
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId // 주문 엔티티의 id(PK)가 곧 분석결과의 id값과 동일하므로 둘을 매핑
    @JoinColumn(name = "order_id", nullable = false, unique = true)
    private Order order;

    // JSONB 타입으로 저장할 필드들
    @JdbcTypeCode(SqlTypes.JSON) // PostgreSQL의 JSONB 타입을 사용하기 위한 설정
    @Column(name = "raw_scraped_data", columnDefinition = "jsonb")
    private Map<String, Object> rawScrapedData;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_ai_log", columnDefinition = "jsonb") // ai가 분석한 결과 데이터
    private Map<String, Object> rawAILog;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "processed_result", columnDefinition = "jsonb") // AI분석 결과에서 가공한 시각화 데이터
    private Map<String, Object> processedResult;

    @Enumerated(EnumType.STRING)
    @Column(name = "report_status", nullable=false,length = 20)
    private ReportStatus reportStatus;

    @Builder
    public AnalysisReport(Order clientOrder, Map<String, Object> rawScrapedData, Map<String, Object> rawAILog) {
        this.order = clientOrder;
        this.rawScrapedData = rawScrapedData;
        this.rawAILog = rawAILog;
        this.reportStatus = ReportStatus.AVAILABLE;
    }

    public void updateAiLogAndProcessedResult(Map<String, Object> aiLog, Map<String, Object> processedResult) {
        this.rawAILog = aiLog;
        this.processedResult = processedResult;
    }

    public void updateReportStatus(ReportStatus newStatus) {
        this.reportStatus = newStatus;
    }

    public void expiredReport() {
        updateReportStatus(ReportStatus.EXPIRED);
        this.rawScrapedData = null;
        this.rawAILog = null;
        this.processedResult = null;
    }

    public enum ReportStatus {
        // 이용 가능, 만료
        AVAILABLE, EXPIRED
    }
}
