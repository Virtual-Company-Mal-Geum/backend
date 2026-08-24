package com.malgeum.geo.service;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.malgeum.geo.domain.domain.analysisjob.entity.AnalysisJob;
import com.malgeum.geo.domain.domain.analysisjob.service.AnalysisExecutionService;
import com.malgeum.geo.domain.domain.analysisjob.service.AnalysisJobService;
import com.malgeum.geo.domain.domain.analysisjob.service.AnalysisJobService.ClaimedJob;
import com.malgeum.geo.domain.domain.order.entity.Order;
import com.malgeum.geo.domain.domain.order.service.OrderService;
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
    private final OrderService orderService;

    // TODO: 재분석 프로세스를 3번 다 해보고나서야 다음꺼로 넘어갈까? || A프로세스 분석 -> B -> C -> A 식으로 순차적이게
    // 할까?
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
        AnalysisJob job = analysisJobService.getAnalysisJob(jobId);
        log.info("[AsyncWorker] 큐 작업 점유 성공 - jobId: {}, orderId: {}, {}번째 시도", jobId, orderId, job.getAttempts());

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

    public void processSynchronously(Long orderId) {
        log.info("[AsyncWorker] 동기 분석 시작 - orderId: {}", orderId);
        analysisJobService.markProcessing(orderId);
        try {
            processClaimedOrder(orderId);
            analysisJobService.markSucceeded(orderId);
            log.info("[AsyncWorker] 동기 분석 성공 - orderId: {}", orderId);
        } catch (Exception e) {
            analysisJobService.markFailureOrRetry(orderId, e);
            log.error("[AsyncWorker] 동기 분석 실패 - orderId: {}", orderId, e);
            throw e;
        }
    }

    protected void processClaimedOrder(Long orderId) {
        AnalysisReportContext context = buildReportContext(orderId);

        analysisExecutionService.saveAnalysisReport(
                orderId,
                context.scrapedData().refinedHtmlText(),
                context.aiLogMap());
    }

    private AnalysisReportContext buildReportContext(Long orderId) {
        Order order = orderService.getOrder(orderId);
        ScrapedData scrapedData = geoScrapingService.extractDataForAi(
                order.getTargetUrl(),
                order.getDomainStatus());
        GeoEvaluationRequest aiRequest = GeoEvaluationRequest.from(scrapedData);
        GeoEvaluationResponse aiResponse = geoAiService.evaluateTarget(aiRequest);

        if (!"success".equals(aiResponse.status())) {
            throw new IllegalStateException("AI 서버 처리 실패: " + aiResponse.reason());
        }

        String detail = aiResponse.detail() != null ? aiResponse.detail() : "";

        Map<String, Object> aiLogMap = new HashMap<>();
        aiLogMap.put("detail", detail);
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode aiJson = mapper.readTree(detail);
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


    private record AnalysisReportContext(ScrapedData scrapedData, Map<String, Object> aiLogMap) {
    }
}
