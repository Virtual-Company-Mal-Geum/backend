package com.malgeum.geo.service;

import java.util.HashMap;
import java.util.Map;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.malgeum.geo.domain.domain.AnalysisReport;
import com.malgeum.geo.domain.domain.Order;
import com.malgeum.geo.dto.GeoEvaluationRequest;
import com.malgeum.geo.dto.GeoEvaluationResponse;
import com.malgeum.geo.dto.ScrapedData;
import com.malgeum.geo.global.common.AnalysisReportRepository;
import com.malgeum.geo.global.common.OrderRepository;

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
            order.updateStatus(Order.JobStatus.PROCESSING);

            ScrapedData scrapedData = geoScrapingService.extractDataForAi(order.getTargetUrl(),
                    order.getDomainStatus());
            GeoEvaluationRequest aiRequest = GeoEvaluationRequest.from(scrapedData);
            GeoEvaluationResponse aiResponse = geoAiService.evaluateTarget(aiRequest);

            if ("success".equals(aiResponse.status())) {
                String content = aiResponse.content() != null ? aiResponse.content() : "";

                // AI 응답 JSON에서 suggested_json_ld 추출
                Map<String, Object> aiLogMap = new HashMap<>();
                aiLogMap.put("content", content);
                try {
                    ObjectMapper mapper = new ObjectMapper();
                    JsonNode aiJson = mapper.readTree(content);
                    JsonNode suggestedJsonLd = aiJson.get("suggested_json_ld");
                    if (suggestedJsonLd != null && !suggestedJsonLd.isNull()) {
                        aiLogMap.put("suggested_json_ld", mapper.convertValue(suggestedJsonLd, Object.class));
                        log.info("[AsyncWorker] suggested_json_ld 추출 성공 - OrderID: {}", orderId);
                    }
                } catch (Exception parseEx) {
                    log.warn("[AsyncWorker] suggested_json_ld 파싱 실패 (무시) - OrderID: {}, 원인: {}",
                            orderId, parseEx.getMessage());
                }

                AnalysisReport report = AnalysisReport.builder()
                        .clientOrder(order)
                        .rawScrapedData(Map.of("htmlText", scrapedData.htmlText()))
                        .rawAILog(aiLogMap)
                        .build();
                analysisReportRepository.save(report);
                order.updateStatus(Order.JobStatus.SUCCESS);
                log.info("[AsyncWorker] 분석 완료 및 저장 성공 - OrderID: {}", orderId);
            } else {
                order.updateStatus(Order.JobStatus.FAILED);
                log.info("[AsyncWorker] AI 서버 분석 실패 - OrderID: {}, 사유: {}", orderId, aiResponse.content());
            }
        } catch (Exception e) {
            log.error("[AsyncWorker] 작업 중 치명적 오류 발생 - OrderID: {}", orderId, e);
            order.updateStatus(Order.JobStatus.FAILED);
        }
    }

    private Map<String, Object> parseJsonMap(String jsonString) throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.readValue(jsonString, new TypeReference<Map<String, Object>>() {
        });
    }
}
