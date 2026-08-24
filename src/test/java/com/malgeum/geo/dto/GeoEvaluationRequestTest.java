package com.malgeum.geo.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GeoEvaluationRequestTest {

    @Test
    @DisplayName("news/ecommerce/education은 AI 요청 생성을 통과하고, domain은 소문자 라우팅 키 그대로 담긴다.")
    void from_AllowsSupportedDomainsAsLowercaseRoutingKey() {
        for (String domain : new String[] { "news", "ecommerce", "education" }) {
            ScrapedData scrapedData = new ScrapedData("https://example.com", domain, "본문", null);

            GeoEvaluationRequest request = GeoEvaluationRequest.from(scrapedData);

            assertThat(request.domain()).isEqualTo(domain);
        }
    }

    @Test
    @DisplayName("tech_blog처럼 AI 서버가 서빙하지 않는 domain은 AI 요청 생성 직전에 차단된다.")
    void from_RejectsTechBlog() {
        ScrapedData scrapedData = new ScrapedData("https://example.com", "tech_blog", "본문", null);

        assertThatThrownBy(() -> GeoEvaluationRequest.from(scrapedData))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("대문자 enum 이름(NEWS 등)처럼 라우팅 키가 아닌 값도 차단된다.")
    void from_RejectsUppercaseEnumNameInsteadOfRoutingKey() {
        ScrapedData scrapedData = new ScrapedData("https://example.com", "NEWS", "본문", null);

        assertThatThrownBy(() -> GeoEvaluationRequest.from(scrapedData))
                .isInstanceOf(IllegalStateException.class);
    }
}
