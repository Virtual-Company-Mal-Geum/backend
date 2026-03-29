package com.malgeum.geo.serivce;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.malgeum.geo.service.GeoScrapingService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class GeoScrapingServiceTest {
    // Spring Bean 주입 없이 순수 실제 객체로 테스트
    private final GeoScrapingService geoScrapingService = new GeoScrapingService();

    @Test
    @DisplayName("실제 웹사이트에 접속하여 본문 텍스트와 JSON-LD를 성공적으로 추출해야 한다.")
    void extractDataForAi_WithRealUrl_ShouldReturnScrapedData() {
        // given: 타겟 URL 설정 
        // https://www.apple.com/kr/
        // https://www.yna.co.kr/
        String url = "https://www.yna.co.kr/";
        
        // when: 스크래핑 서비스 실행
        long startTime = System.currentTimeMillis();
        GeoScrapingService.ScrapedData result = geoScrapingService.extractDataForAi(url);
        long endTime = System.currentTimeMillis();

        // then: 결과 검증
        log.info("스크래핑 소요 시간: {} ms", (endTime - startTime));
        log.info("추출된 본문 길이: {} 자", result.htmlText().length());
        log.info("추출된 JSON-LD: {}", result.jsonLd());

        assertNotNull(result);
        assertThat(result.htmlText()).isNotBlank();
        
        // 3. 만약 해당 사이트에 JSON-LD가 있다면, "{" 로 시작하는 포맷이어야 함
        // (주의: 타겟 URL에 JSON-LD가 없는 사이트라면 이 검증은 빼거나 조건부로 처리해야 합니다)
        if(!result.jsonLd().equals("[]")){
            assertThat(result.jsonLd()).contains("{").contains("}");
        }
    }
}
