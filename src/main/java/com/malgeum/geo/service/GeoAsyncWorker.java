package com.malgeum.geo.service;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.malgeum.geo.domain.domain.analysisjob.service.AnalysisExecutionService;
import com.malgeum.geo.domain.domain.analysisjob.service.AnalysisJobService;
import com.malgeum.geo.domain.domain.analysisjob.service.AnalysisJobService.ClaimedJob;
import com.malgeum.geo.dto.GeoEvaluationRequest;
import com.malgeum.geo.dto.GeoEvaluationResponse;
import com.malgeum.geo.dto.ScrapedData;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class GeoAsyncWorker {
    private final GeoScrapingService geoScrapingService;
    private final GeoAiService geoAiService;
    private final AnalysisJobService analysisJobService;
    private final AnalysisExecutionService analysisExecutionService;

    @SuppressWarnings("null")
    @Scheduled(fixedDelayString = "${analysis.job.poll-delay-ms:1000}")
    public void pollAndProcess() {
        while (processNextJob()) {
            // 큐에 대기 중인 작업이 있는 동안 1건씩 순차 처리
        }
    }

    private boolean processNextJob() {
        Optional<ClaimedJob> claimedJobOpt = analysisJobService.claimNextJob();
        if (claimedJobOpt.isEmpty()) {
            return false;
        }

        ClaimedJob claimedJob = claimedJobOpt.get();
        Long jobId = claimedJob.jobId();
        Long orderId = claimedJob.orderId();
        log.info("[AsyncWorker] 큐 작업 점유 성공 - jobId: {}, orderId: {}", jobId, orderId);

        try {
            processClaimedOrder(orderId);
            analysisJobService.markSucceeded(jobId);
            log.info("[AsyncWorker] 작업 처리 성공 - jobId: {}, orderId: {}", jobId, orderId);
        } catch (Exception e) {
            analysisJobService.markFailureOrRetry(jobId, e);
            log.error("[AsyncWorker] 작업 처리 실패 - jobId: {}, orderId: {}", jobId, orderId, e);
        }
        return true;
    }

    protected void processClaimedOrder(Long orderId) {
        AnalysisReportContext context = buildReportContext(orderId);

        analysisExecutionService.saveAnalysisReport(
                orderId,
                context.scrapedData().htmlText(),
                context.aiLogMap());
    }

    private AnalysisReportContext buildReportContext(Long orderId) {
        var order = analysisJobService.getOrderForProcessing(orderId);
        ScrapedData scrapedData = geoScrapingService.extractDataForAi(
                order.getTargetUrl(),
                order.getDomainStatus());
        GeoEvaluationRequest aiRequest = GeoEvaluationRequest.from(scrapedData);
        GeoEvaluationResponse aiResponse = geoAiService.evaluateTarget(aiRequest);

        if (!"success".equals(aiResponse.status())) {
            throw new IllegalStateException("AI 서버 처리 실패: " + aiResponse.content());
        }

        String content = aiResponse.content() != null ? aiResponse.content() : "";

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

        return new AnalysisReportContext(scrapedData, aiLogMap);
    }

    // private Map<String, Object> parseJsonMap(String jsonString) throws Exception {
    //     ObjectMapper objectMapper = new ObjectMapper();
    //     return objectMapper.readValue(jsonString, new TypeReference<Map<String, Object>>() {
    //     });
    // }

    private record AnalysisReportContext(ScrapedData scrapedData, Map<String, Object> aiLogMap) {
    }
}
