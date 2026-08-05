package com.malgeum.geo.domain.domain.analysisreport.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.malgeum.geo.domain.domain.analysisreport.entity.AnalysisReport;
import com.malgeum.geo.domain.domain.analysisreport.entity.AnalysisReport.ReportStatus;

public interface AnalysisReportRepository extends JpaRepository<AnalysisReport, Long> {
    Optional<AnalysisReport> findByOrderId(Long orderId);

    List<AnalysisReport> findAllByOrder_Client_IdAndReportStatusNotOrderByCreatedAtDesc(Long clientId,
            ReportStatus reportStatus);
}
