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
public class AnalysisExecutionService { //AnalysisJobService와 달리, 실제 분석을 수행하는 서비스. (AI 모델 호출, 결과 저장 등)
    private final OrderRepository orderRepository; //orderRepository와 analysisReportRepository에 대한 의존성이 추가되기에 따로 독립시켜놓음.
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
