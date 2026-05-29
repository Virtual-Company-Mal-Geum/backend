package com.malgeum.geo.service;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.malgeum.geo.dto.GeoEvaluationRequest;
import com.malgeum.geo.dto.GeoEvaluationResponse;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class GeoAiService {

    private final RestClient restClient;

    public GeoAiService(RestClient.Builder restClientBuilder) {
        //http://localhost:8888/
        //https://desktop-75bjpd-lab4090.tail6dd0ea.ts.net:8443/
        this.restClient = restClientBuilder
                .baseUrl("https://desktop-75bjpd-lab4090.tail6dd0ea.ts.net:8443/") 
                .build();
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
            return new GeoEvaluationResponse("error","text","AI 서버 응답 지연(Timeout).");
        } catch (RestClientResponseException e) {
            log.error("[GeoAiService] AI 연산 중 오류 발생 (Status: {}): {}", e.getStatusCode(), e.getMessage());
            return new GeoEvaluationResponse("error","text", "AI 연산 중 오류 발생: " + e.getStatusCode());
        }
    }
}
