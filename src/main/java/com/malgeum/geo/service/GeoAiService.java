package com.malgeum.geo.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.malgeum.geo.dto.GeoEvaluationRequest;
import com.malgeum.geo.dto.GeoEvaluationResponse;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class GeoAiService {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public GeoAiService(RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            @Value("${ai.server.base-url}") String aiServerBaseUrl) {
        this.restClient = restClientBuilder
                .baseUrl(aiServerBaseUrl)
                .build();
        this.objectMapper = objectMapper;
    }

    public GeoEvaluationResponse evaluateTarget(GeoEvaluationRequest aiRequest) {
        log.info("[GeoAiService] AI 서버로 분석 요청을 전송합니다. URL: {}", aiRequest.url());
        try {
            return restClient.post()
                    .uri("/evaluate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(aiRequest)
                    .retrieve()
                    .body(GeoEvaluationResponse.class);

        } catch (ResourceAccessException e) {
            log.error("[GeoAiService] AI 서버 응답 지연 또는 다운: {}", e.getMessage());
            return errorResponse("timeout", "AI 서버 응답 지연(Timeout).");
        } catch (RestClientResponseException e) {
            String reason = classifyHttpReason(e.getStatusCode());
            String detailMessage = extractDetailMessage(e.getResponseBodyAsString());
            log.error("[GeoAiService] AI 연산 중 오류 발생 (Status: {}, reason: {}): {}",
                    e.getStatusCode(), reason, detailMessage);
            return errorResponse(reason, detailMessage);
        }
    }

    private GeoEvaluationResponse errorResponse(String reason, String detail) {
        return new GeoEvaluationResponse("error", null, null, reason, detail, null, null, null, null);
    }

    /**
     * HTTP 상태 코드별로 재시도/사용자 오류 여부를 구분할 수 있도록 reason을 분류한다.
     * 429(rate limit)·422(입력 검증)·400(잘못된 요청)·5xx(서버/추론 오류)를 하나의 값으로 뭉개지 않는다.
     */
    private String classifyHttpReason(HttpStatusCode status) {
        int code = status.value();
        if (code == 429) {
            return "rate-limited";
        }
        if (code == 422) {
            return "invalid-input";
        }
        if (code == 400) {
            return "bad-request";
        }
        if (status.is5xxServerError()) {
            return "server-error";
        }
        return "http-error";
    }

    /** AI 서버는 오류 시 FastAPI 기본 형식 {"detail": "..."}을 반환한다. */
    private String extractDetailMessage(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return "AI 서버 오류 (응답 본문 없음)";
        }
        try {
            JsonNode body = objectMapper.readTree(responseBody);
            JsonNode detailNode = body.get("detail");
            if (detailNode != null && detailNode.isTextual()) {
                return detailNode.asText();
            }
        } catch (Exception e) {
            log.debug("[GeoAiService] 오류 응답 본문이 JSON이 아니어서 원문을 그대로 사용합니다: {}", e.getMessage());
        }
        return responseBody;
    }
}
