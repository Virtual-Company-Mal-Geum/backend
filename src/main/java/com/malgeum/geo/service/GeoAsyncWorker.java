package com.malgeum.geo.service;

import java.util.Map;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.malgeum.geo.domain.domain.AnalysisReport;
import com.malgeum.geo.domain.domain.Order;
import com.malgeum.geo.global.common.AnalysisReportRepository;
import com.malgeum.geo.global.common.OrderRepository;
import com.malgeum.geo.service.GeoAiService.GeoEvaluationResponse;
import com.malgeum.geo.service.GeoScrapingService.ScrapedData;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class GeoAsyncWorker {
    private final GeoScrapingService geoScrapingService;
    private final GeoAiService geoAiService;
    private final OrderRepository orderRepository;
    private final AnalysisReportRepository analysisReportRepository;

    // 이전에 설정한 커스텀 스레드 풀에서 실행되도록 지정
    @SuppressWarnings("null")
    @Async("taskExecutor")
    @Transactional
    public void processAnalysis(Long orderId) {
        log.info("[AsyncWorker] 백그라운드 분석 시작 - OrderID: {}", orderId);
        if (orderId == null || orderId <= 0) {
            log.error("[AsyncWorker] 유효하지 않은 OrderID: {}", orderId);
            return;
        }

        Order order = null;
        try {
            order = orderRepository.findById(orderId).orElseThrow();
            // 1. 상태를 PROCESSING(진행 중)으로 변경
            order.updateStatus(Order.JobStatus.PROCESSING);
            String targetUrl = order.getTargetUrl();
            // 2. Jsoup 스크래핑 실행 (부품 1)
            ScrapedData scrapedData = geoScrapingService.extractDataForAi(targetUrl);
            // 3. AI 서버에 평가 요청 (부품 2)
            GeoEvaluationResponse aiResponse = geoAiService.evaluateTarget(targetUrl, scrapedData.htmlText(),
                    scrapedData.jsonLd());

            // 4. 성공: 결과 리포트(AnalysisReport) 생성 및 상태 업데이트
            if (aiResponse.status().equals("success")) {
                AnalysisReport report = AnalysisReport.builder()
                        .clientOrder(order)
                        .rawScrapedData(Map.of("htmlText", scrapedData.htmlText(), "jsonLd", scrapedData.jsonLd()))
                        .rawAILog(parseJsonMap(aiResponse.result()))
                        .build();
                analysisReportRepository.save(report);
                order.updateStatus(Order.JobStatus.SUCCESS);
                log.info("[AsyncWorker] 분석 완료 및 저장 성공 - OrderID: {}", orderId);
            } else {
                // AI 서버에서 에러 응답을 준 경우
                order.updateStatus(Order.JobStatus.FAILED);
                log.info("[AsyncWorker] AI 서버 분석 실패 - OrderID: {}, 사유: {}", orderId, aiResponse.result());
            }
        } catch (Exception e) {
            log.error("[AsyncWorker] 작업 중 치명적 오류 발생 - OrderID: {}", orderId, e);
            order.updateStatus(Order.JobStatus.FAILED);
        }
    }

    private Map<String, Object> parseJsonMap(String jsonString) throws Exception {
        // AI 응답에서 JSON 문자열을 Map으로 변환
        ObjectMapper objectMapper = new ObjectMapper();
        Map<String, Object> aiResultMap = objectMapper.readValue(jsonString,
                new TypeReference<Map<String, Object>>() {
                });
        return aiResultMap;
    }
}
