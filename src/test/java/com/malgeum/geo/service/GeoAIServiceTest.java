package com.malgeum.geo.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import java.io.IOException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.client.RestClientTest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.client.MockRestServiceServer;

import com.malgeum.geo.dto.GeoEvaluationRequest;
import com.malgeum.geo.dto.GeoEvaluationResponse;
import com.malgeum.geo.dto.ScrapedData;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestClientTest(GeoAiService.class)
@TestPropertySource(properties = "ai.server.base-url=http://localhost:8888")
public class GeoAIServiceTest {

    @MockitoBean
    private JpaMetamodelMappingContext mockJpaMetamodelMappingContext;

    @Autowired
    private GeoAiService geoAiService;

    @Autowired
    private MockRestServiceServer mockServer; // 가짜 파이썬 AI 서버

    private GeoEvaluationRequest anyRequest() {
        ScrapedData scrapedData = new ScrapedData("http://example.com", "ecommerce", null, null);
        return GeoEvaluationRequest.from(scrapedData);
    }

    @Test
    @DisplayName("AI 서버에 정상적인 요청을 보내고, 결과를 성공적으로 파싱해야 한다.")
    public void evaluateTarget_ShouldReturnSuccess() {
        String mockJsonResponse = "{\"status\": \"success\", \"domain\": \"ecommerce\", \"result\": {\"score\": 95}}";
        mockServer.expect(requestTo("http://localhost:8888/evaluate"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andRespond(withSuccess(mockJsonResponse, MediaType.APPLICATION_JSON));

        GeoEvaluationResponse aiResponse = geoAiService.evaluateTarget(anyRequest());

        assertEquals("success", aiResponse.status());
        assertEquals(95, aiResponse.result().get("score").asInt());
        mockServer.verify();
    }

    @Test
    @DisplayName("success 응답에 content_warning이 섞여 와도 경고 필드까지 그대로 파싱해야 한다.")
    public void evaluateTarget_ShouldParseSuccessWithContentWarning() {
        String mockJsonResponse = "{\"status\": \"success\", \"domain\": \"education\", \"result\": {\"score\": 60}, "
                + "\"content_warning\": \"content_too_short\", \"main_content_length\": 120}";
        mockServer.expect(requestTo("http://localhost:8888/evaluate"))
                .andRespond(withSuccess(mockJsonResponse, MediaType.APPLICATION_JSON));

        GeoEvaluationResponse aiResponse = geoAiService.evaluateTarget(anyRequest());

        assertEquals("success", aiResponse.status());
        assertEquals("content_too_short", aiResponse.contentWarning());
        assertEquals(120, aiResponse.mainContentLength());
    }

    @Test
    @DisplayName("insufficient_content 응답은 reason/detail/main_content_length를 그대로 파싱해야 한다.")
    public void evaluateTarget_ShouldParseInsufficientContent() {
        String mockJsonResponse = "{\"status\": \"insufficient_content\", \"reason\": \"content_too_short\", "
                + "\"domain\": \"news\", \"main_content_length\": 10, \"detail\": \"채점 가능한 본문이 사실상 없습니다.\"}";
        mockServer.expect(requestTo("http://localhost:8888/evaluate"))
                .andRespond(withSuccess(mockJsonResponse, MediaType.APPLICATION_JSON));

        GeoEvaluationResponse aiResponse = geoAiService.evaluateTarget(anyRequest());

        assertEquals("insufficient_content", aiResponse.status());
        assertEquals("content_too_short", aiResponse.reason());
        assertEquals(10, aiResponse.mainContentLength());
        assertNull(aiResponse.result());
    }

    @Test
    @DisplayName("HTTP 5xx 오류는 blank-text가 아니라 server-error로 분류되고, FastAPI detail 메시지를 그대로 담아야 한다.")
    public void evaluateTarget_ShouldClassifyServerErrorAndExtractDetail() {
        String errorBody = "{\"detail\":\"AI 추론 서버(vLLM) 오류: 400 (model=education)\"}";
        mockServer.expect(requestTo("http://localhost:8888/evaluate"))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY)
                        .body(errorBody)
                        .contentType(MediaType.APPLICATION_JSON));

        GeoEvaluationResponse aiResponse = geoAiService.evaluateTarget(anyRequest());

        assertEquals("error", aiResponse.status());
        assertEquals("server-error", aiResponse.reason());
        assertEquals("AI 추론 서버(vLLM) 오류: 400 (model=education)", aiResponse.detail());
    }

    @Test
    @DisplayName("HTTP 429는 rate-limited로 분류해야 한다.")
    public void evaluateTarget_ShouldClassifyRateLimited() {
        mockServer.expect(requestTo("http://localhost:8888/evaluate"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                        .body("{\"detail\":\"큐가 가득 찼습니다.\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        GeoEvaluationResponse aiResponse = geoAiService.evaluateTarget(anyRequest());

        assertEquals("error", aiResponse.status());
        assertEquals("rate-limited", aiResponse.reason());
    }

    @Test
    @DisplayName("연결 자체가 실패(Timeout 등)하면 timeout으로 분류해야 한다.")
    public void evaluateTarget_ShouldClassifyTimeout() {
        mockServer.expect(requestTo("http://localhost:8888/evaluate"))
                .andRespond(request -> {
                    throw new IOException("simulated timeout");
                });

        GeoEvaluationResponse aiResponse = geoAiService.evaluateTarget(anyRequest());

        assertEquals("error", aiResponse.status());
        assertEquals("timeout", aiResponse.reason());
        assertTrue(aiResponse.detail().contains("Timeout"));
    }
}
