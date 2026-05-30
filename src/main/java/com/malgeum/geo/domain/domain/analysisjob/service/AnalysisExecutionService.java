package com.malgeum.geo.domain.domain.analysisjob.service;

import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.malgeum.geo.domain.domain.analysisreport.entity.AnalysisReport;
import com.malgeum.geo.domain.domain.analysisreport.repository.AnalysisReportRepository;
import com.malgeum.geo.domain.domain.order.entity.Order;
import com.malgeum.geo.domain.domain.order.repository.OrderRepository;
import com.malgeum.geo.global.common.DataNotFoundException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AnalysisExecutionService {
    private final OrderRepository orderRepository;
    private final AnalysisReportRepository analysisReportRepository;

    @Transactional
    public void saveAnalysisReport(Long orderId, String htmlText, Map<String, Object> aiLogMap) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new DataNotFoundException("Order not found. id=" + orderId));

        AnalysisReport report = AnalysisReport.builder()
                .clientOrder(order)
                .rawScrapedData(Map.of("htmlText", htmlText))
                .rawAILog(aiLogMap)
                .build();
        analysisReportRepository.save(report);
    }
}
