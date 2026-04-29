package com.malgeum.geo.serivce;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.client.RestClientTest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.client.MockRestServiceServer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.malgeum.geo.service.GeoAiService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestClientTest(GeoAiService.class)
public class GeoAIServiceTest {

    @MockitoBean
    private JpaMetamodelMappingContext mockJpaMetamodelMappingContext;

    @Autowired
    private GeoAiService geoAiService;

    @Autowired
    private MockRestServiceServer mockServer; // 가짜 파이썬 AI 서버

    @SuppressWarnings("null")
    @Test
    @DisplayName("AI 서버에 정상적인 요청을 보내고, 결과를 성공적으로 파싱해야 한다.")
    public void evaluateTarget_ShouldReturnSuccess() {
        // given: 가짜 파이썬 서버가 "/evaluate" 요청을 받으면 어떻게 응답할지 대본을 짭니다.
        String mockJsonResponse = "{\"status\": \"success\", \"result\": \"총점 95점! 훌륭합니다.\"}";
        mockServer.expect(requestTo("http://localhost:8000/evaluate"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andRespond(withSuccess(mockJsonResponse, MediaType.APPLICATION_JSON));

        // when: 우리가 만든 서비스를 실행합니다! (내부적으로 가짜 서버를 찌르게 됩니다)
        ObjectMapper objectMapper = new ObjectMapper();
        GeoAiService.GeoEvaluationResponse response = geoAiService.evaluateTarget(
                "http://example.com",
                "본문 텍스트",
                objectMapper.createObjectNode().put("@type", "WebPage"));
        
        // then: 결과를 검증합니다.
        assertEquals("success", response.status());
        assertEquals("총점 95점! 훌륭합니다.", response.result());
        mockServer.verify(); // 가짜 서버가 예상대로 호출되었는지 검증
    }

}
