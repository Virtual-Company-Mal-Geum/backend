package com.malgeum.geo.service;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.malgeum.geo.domain.ScrapedData;

import lombok.extern.slf4j.Slf4j;

import org.springframework.web.client.ResourceAccessException;

@Slf4j
@Service
public class GeoAiService {

    private final RestClient restClient;

    public GeoAiService(RestClient.Builder restClientBuilder) {

        // [의도] 로컬호스트 주소를 Base URL로 고정.
        // (실무에서는 이 부분을 application.yml의 환경 변수로 분리하는 것을 권장합니다.)
        this.restClient = restClientBuilder
                .baseUrl("http://localhost:8000")
                .build();
    }

    // [의도] 요청/응답 데이터의 불변성을 보장하고 보일러플레이트 코드를 줄이기 위해 Record 사용
    public record GeoEvaluationRequest( 
        @JsonProperty("url")
        String url,

        @JsonProperty("category")
        String category,

        @JsonProperty("html_text")
        String htmlText,

        @JsonProperty("json_ld")
        String jsonLd) {
            public static GeoEvaluationRequest from(ScrapedData scrapedData) {
                ScrapedData normalized = scrapedData.normalized();
                return new GeoEvaluationRequest(
                        normalized.url(),
                        normalized.category(),
                        normalized.htmlText(),
                        normalized.jsonLd()
                );
            }
    }
    

    public record GeoEvaluationResponse(String status, String result) {
    }

    public GeoEvaluationResponse evaluateTarget(GeoEvaluationRequest request) {
        log.info("[GeoAiService] AI 서버로 분석 요청을 전송합니다. URL: {}", request.url());

        try {
            return restClient.post()
                    .uri("/evaluate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(GeoEvaluationResponse.class);

        } catch (ResourceAccessException e) {
            log.error("[GeoAiService] AI 서버 응답 지연 또는 다운: {}", e.getMessage());
            return new GeoEvaluationResponse("error", "AI 서버 응답 지연 (Timeout).");
        } catch (RestClientResponseException e) {
            log.error("[GeoAiService] AI 연산 중 오류 발생 (Status: {}): {}", e.getStatusCode(), e.getMessage());
            return new GeoEvaluationResponse("error", "AI 연산 중 오류 발생: " + e.getStatusCode());
        }
    }
}