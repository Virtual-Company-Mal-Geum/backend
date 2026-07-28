package com.malgeum.geo.domain.domain.analysisreport.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.malgeum.geo.domain.domain.analysisreport.entity.AnalysisReport;
import com.malgeum.geo.domain.domain.order.entity.Order;

public interface AnalysisReportRepository extends JpaRepository<AnalysisReport, Long> {
    Optional<AnalysisReport> findByOrderId(Long orderId);
}
