package com.malgeum.geo.domain.analysisReport;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.hibernate.type.format.jakartajson.JsonBJsonFormatMapper;

import com.malgeum.geo.domain.BaseTimeEntity;
import com.malgeum.geo.domain.clientOrder.ClientOrder;

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
    @MapsId //주문 엔티티의 id(PK)가 곧 분석결과의 id값과 동일하므로 둘을 매핑
    @JoinColumn(name = "order_id", nullable = false)
    private ClientOrder clientOrder;

    // JSONB 타입으로 저장할 필드들
    @JdbcTypeCode(SqlTypes.JSON) // PostgreSQL의 JSONB 타입을 사용하기 위한 설정
    @Column(name = "raw_scraped_data", nullable = false, columnDefinition = "jsonb")
    private JsonBJsonFormatMapper rawScrapedData;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_ai_log", columnDefinition = "jsonb") // ai가 분석한 결과 데이터
    private JsonBJsonFormatMapper rawAILog;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "processed_result", columnDefinition = "jsonb") // AI분석 결과에서 가공한 시각화 데이터
    private JsonBJsonFormatMapper processedResult;
    //

    @Enumerated(EnumType.STRING)
    @Column(name = "report_status", nullable = false, length = 20)
    private ReportStatus reportStatus;

    @Builder
    public AnalysisReport(ClientOrder clientOrder, JsonBJsonFormatMapper rawScrapedData) {
        this.clientOrder = clientOrder;
        this.rawScrapedData = rawScrapedData;
        this.reportStatus = ReportStatus.AVAILABLE;
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
