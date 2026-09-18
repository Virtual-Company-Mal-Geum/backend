package com.malgeum.geo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.malgeum.geo.domain.domain.analysisjob.entity.AnalysisJob;
import com.malgeum.geo.domain.domain.analysisjob.service.AnalysisExecutionService;
import com.malgeum.geo.domain.domain.analysisjob.service.AnalysisJobService;
import com.malgeum.geo.domain.domain.analysisjob.service.AnalysisJobService.ClaimedJob;
import com.malgeum.geo.domain.domain.order.entity.Order;
import com.malgeum.geo.domain.domain.order.entity.Order.DomainStatus;
import com.malgeum.geo.domain.domain.order.service.OrderService;
import com.malgeum.geo.dto.GeoEvaluationResponse;
import com.malgeum.geo.dto.ScrapedData;

/**
 * DB/Spring 컨텍스트 없이 GeoAsyncWorker의 AI 응답 상태 분기(success/insufficient_content/parse_error/error)와
 * aiLogMap 조립 로직만 순수 Mockito로 검증한다. 큐 진입점은 processNextJob()이며, claimNextJob()을 직접
 * 스텁해서 실제 enqueue/DB 없이 바로 검증한다.
 */
class GeoAsyncWorkerResponseHandlingTest {

    // jobId != orderId로 둔다: 과거 실제로 있었던 jobId/orderId 혼용 버그가 재발하면 이 테스트가 바로 실패한다.
    private static final Long JOB_ID = 7L;
    private static final Long ORDER_ID = 42L;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final GeoScrapingService geoScrapingService = mock(GeoScrapingService.class);
    private final GeoAiService geoAiService = mock(GeoAiService.class);
    private final AnalysisJobService analysisJobService = mock(AnalysisJobService.class);
    private final AnalysisExecutionService analysisExecutionService = mock(AnalysisExecutionService.class);
    private final OrderService orderService = mock(OrderService.class);

    private final GeoAsyncWorker worker = new GeoAsyncWorker(
            geoScrapingService, geoAiService, analysisJobService, analysisExecutionService, orderService);

    private JsonNode json(String raw) throws Exception {
        return MAPPER.readTree(raw);
    }

    private void givenOrderAndScrape() {
        Order order = mock(Order.class);
        given(order.getTargetUrl()).willReturn("https://example.com");
        given(order.getDomainStatus()).willReturn(DomainStatus.EDUCATION);
        given(orderService.getOrder(ORDER_ID)).willReturn(order);
        given(geoScrapingService.extractDataForAi(anyString(), any()))
                .willReturn(new ScrapedData("https://example.com", "education", "[DOCUMENT_TYPE]\n\nEDUCATION_ARTICLE", null));
    }

    /** claimNextJob()이 이 건을 막 집어온 것처럼 목을 세팅한다 — processNextJob()을 바로 태우기 위한 최소 입력. */
    private void givenClaimedJob() {
        given(analysisJobService.claimNextJob())
                .willReturn(Optional.of(new ClaimedJob(JOB_ID, ORDER_ID)));
        AnalysisJob job = mock(AnalysisJob.class);
        given(job.getAttempts()).willReturn(1);
        given(analysisJobService.getAnalysisJob(JOB_ID)).willReturn(job);
    }

    @Test
    @DisplayName("success 응답이면 result가 aiLogMap에 저장되고 예외 없이 성공 처리된다.")
    void success_StoresResultAndSucceeds() throws Exception {
        givenClaimedJob();
        givenOrderAndScrape();
        given(geoAiService.evaluateTarget(any())).willReturn(new GeoEvaluationResponse(
                "success", "education", json("{\"score\":88}"), null, null, null, null, null, null));

        boolean processed = worker.processNextJob();

        assertThat(processed).isTrue();
        ArgumentCaptor<Map<String, Object>> logCaptor = ArgumentCaptor.forClass(Map.class);
        verify(analysisExecutionService).saveAnalysisReport(eq(ORDER_ID), anyString(), logCaptor.capture());
        assertThat(logCaptor.getValue()).containsKey("result");
        assertThat(logCaptor.getValue()).doesNotContainKeys("content_warning", "main_content_length");
        verify(analysisJobService).markSucceeded(ORDER_ID);
        verify(analysisJobService, never()).markFailureOrRetry(any(), any());
    }

    @Test
    @DisplayName("success + content_warning/cjk_normalized/jsonld_input이 함께 오면 실패로 취급하지 않고 부가 필드까지 저장한다.")
    void successWithOptionalFields_StillSucceedsAndCarriesFieldsThrough() throws Exception {
        givenClaimedJob();
        givenOrderAndScrape();
        given(geoAiService.evaluateTarget(any())).willReturn(new GeoEvaluationResponse(
                "success", "education", json("{\"score\":55}"),
                null, null,
                "content_too_short", 120, 3, json("{\"record\":{}}")));

        worker.processNextJob();

        ArgumentCaptor<Map<String, Object>> logCaptor = ArgumentCaptor.forClass(Map.class);
        verify(analysisExecutionService).saveAnalysisReport(eq(ORDER_ID), anyString(), logCaptor.capture());
        Map<String, Object> aiLogMap = logCaptor.getValue();
        assertThat(aiLogMap.get("content_warning")).isEqualTo("content_too_short");
        assertThat(aiLogMap.get("main_content_length")).isEqualTo(120);
        assertThat(aiLogMap.get("cjk_normalized")).isEqualTo(3);
        assertThat(aiLogMap).containsKey("jsonld_input");
        verify(analysisJobService).markSucceeded(ORDER_ID);
    }

    @Test
    @DisplayName("insufficient_content는 크롤 실패로 분류되어 저장 없이 markFailureOrRetry로 넘어간다.")
    void insufficientContent_MarksFailureAndNeverSaves() {
        givenClaimedJob();
        givenOrderAndScrape();
        given(geoAiService.evaluateTarget(any())).willReturn(new GeoEvaluationResponse(
                "insufficient_content", "education", null, "content_too_short",
                "채점 가능한 본문이 사실상 없습니다.", null, 10, null, null));

        boolean processed = worker.processNextJob();

        assertThat(processed).isTrue();
        verify(analysisExecutionService, never()).saveAnalysisReport(any(), any(), any());
        ArgumentCaptor<Exception> exCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(analysisJobService).markFailureOrRetry(eq(ORDER_ID), exCaptor.capture());
        assertThat(exCaptor.getValue())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("본문 부족");
    }

    @Test
    @DisplayName("parse_error는 모델 응답 파싱 실패로 구분되어 markFailureOrRetry로 넘어간다.")
    void parseError_MarksFailure() {
        givenClaimedJob();
        givenOrderAndScrape();
        given(geoAiService.evaluateTarget(any())).willReturn(new GeoEvaluationResponse(
                "parse_error", null, null, null, "생성물을 JSON으로 파싱하지 못했습니다.", null, null, null, null));

        worker.processNextJob();

        verify(analysisExecutionService, never()).saveAnalysisReport(any(), any(), any());
        ArgumentCaptor<Exception> exCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(analysisJobService).markFailureOrRetry(eq(ORDER_ID), exCaptor.capture());
        assertThat(exCaptor.getValue())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("파싱 실패");
    }

    @Test
    @DisplayName("GeoAiService가 합성한 error(예: rate-limited)는 blank-text가 아니라 실제 reason으로 예외 메시지에 남는다.")
    void httpError_ClassifiedReasonReachesFailureHandler() {
        givenClaimedJob();
        givenOrderAndScrape();
        given(geoAiService.evaluateTarget(any())).willReturn(new GeoEvaluationResponse(
                "error", null, null, "rate-limited", "큐가 가득 찼습니다.", null, null, null, null));

        worker.processNextJob();

        verify(analysisExecutionService, never()).saveAnalysisReport(any(), any(), any());
        ArgumentCaptor<Exception> exCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(analysisJobService).markFailureOrRetry(eq(ORDER_ID), exCaptor.capture());
        assertThat(exCaptor.getValue()).hasMessageContaining("rate-limited");
    }
}
