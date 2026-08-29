package com.malgeum.geo.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.malgeum.geo.domain.domain.order.entity.Order.DomainStatus;
import com.malgeum.geo.dto.GeoEvaluationRequest;
import com.malgeum.geo.dto.GeoEvaluationResponse;
import com.malgeum.geo.dto.ScrapedData;

/**
 * 실제 크롤링 파이프라인(GeoScrapingService)이 만들어낸 입력을, application.yaml의
 * ai.server.base-url(Tailscale 주소)로 떠 있는 실제 AI 서버 /evaluate로 보내서
 * 입력 형식이 유효한지, 그리고 AI가 실제로 어떤 응답을 주는지 눈으로 확인하기 위한 수동(라이브) 테스트다.
 *
 * Mock 서버가 아닌 실제 네트워크 호출이므로, AI 서버가 켜져 있고 Tailscale로 접근 가능할 때만
 * 개별 실행할 것 (전체 테스트 스위트에 포함하고 싶지 않다면 다시 @Disabled를 붙이면 된다).
 *
 * log.info 대신 System.out을 쓰는 이유: Gradle 기본 로그 레벨(--info 없이)에서는
 * log.info가 콘솔에 보이지 않아 응답 내용을 놓치기 쉽다.
 */
class GeoAiServerLiveTest {

    // application.yaml 의 ai.server.base-url 과 동일한 값.
    private static final String AI_SERVER_BASE_URL = "https://desktop-75bjpd-lab4090.tail6dd0ea.ts.net:8443";

    // 실제로 크롤링해볼 대상. 확인하고 싶은 URL/도메인으로 바꿔서 실행하면 된다.
    private static final String TARGET_URL = "https://www.yna.co.kr/";
    private static final DomainStatus TARGET_DOMAIN = DomainStatus.NEWS;

    @Test
    @DisplayName("실제 크롤링 결과를 AI 서버 /evaluate로 전송하면 정상 응답을 받아야 한다.")
    void crawlAndSendToRealAiServer() throws Exception {
        ObjectMapper prettyMapper = new ObjectMapper();

        GeoScrapingService scrapingService = new GeoScrapingService();
        ScrapedData scrapedData = scrapingService.extractDataForAi(TARGET_URL, TARGET_DOMAIN);

        GeoEvaluationRequest aiRequest = GeoEvaluationRequest.from(scrapedData);

        System.out.println("\n===== [1] AI 서버로 보낸 요청 (POST " + AI_SERVER_BASE_URL + "/evaluate) =====");
        System.out.println(prettyMapper.writerWithDefaultPrettyPrinter().writeValueAsString(aiRequest));

        GeoAiService geoAiService = new GeoAiService(RestClient.builder(), new ObjectMapper(), AI_SERVER_BASE_URL);
        GeoEvaluationResponse aiResponse = geoAiService.evaluateTarget(aiRequest);

        System.out.println("\n===== [2] AI 서버가 보낸 응답 =====");
        System.out.println(prettyMapper.writerWithDefaultPrettyPrinter().writeValueAsString(aiResponse));
        System.out.println("=====================================\n");

        assertThat(aiResponse.status())
                .as("AI 서버가 크롤링 입력을 정상 처리했는지 확인 (reason: %s, detail: %s)",
                        aiResponse.reason(), aiResponse.detail())
                .isNotEqualTo("error");
    }
}
