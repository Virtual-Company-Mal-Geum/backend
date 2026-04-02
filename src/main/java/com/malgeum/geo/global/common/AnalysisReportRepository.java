package com.malgeum.geo.global.common;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.malgeum.geo.domain.domain.AnalysisReport;

public interface AnalysisReportRepository extends JpaRepository<AnalysisReport, Long> {
    Optional<AnalysisReport> findByOrderId(Long orderId);
}
