package com.malgeum.geo.service;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.sun.net.httpserver.HttpServer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.malgeum.geo.domain.domain.order.entity.Order.DomainStatus;
import com.malgeum.geo.dto.ScrapedData;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class GeoScrapingServiceTest {
    // Spring Bean 주입 없이 순수 실제 객체로 테스트
    private final GeoScrapingService geoScrapingService = new GeoScrapingService();

    @Test
    @DisplayName("실제 웹사이트에 접속하여 본문 텍스트와 JSON-LD를 성공적으로 추출해야 한다.")
    void extractDataForAi_WithRealUrl_ShouldReturnScrapedData() throws Exception {
        // given: 타겟 URL 설정
        // 정적 페이지 : https://www.apple.com/kr/shop/buy-iphone/iphone-17-pro
        // 동적 페이지 : https://www.instagram.com/p/DYzfA56jQ2f/
        // https://www.kyonggi.ac.kr/www/contents.do?key=8418&
        String url = "https://www.megastudy.net/tpass/2026/0604_ps34470/main.asp?SubMainType=S&mOne=season&mTwo=693";

        // when: 스크래핑 서비스 실행
        long startTime = System.currentTimeMillis();
        ScrapedData result = geoScrapingService.extractDataForAi(url, DomainStatus.ECOMMERCE);
        long endTime = System.currentTimeMillis();

        // then: 결과 검증
        log.info("스크래핑 소요 시간: {} ms", (endTime - startTime));
        log.info("추출된 본문 길이: {} 자", result.refinedHtmlText().length());
        log.info("추출된 본문 내용 : {}", result.refinedHtmlText());
        log.info("추출된 JSON-LD: {}", result.jsonLd());

        assertNotNull(result);
        assertThat(result.refinedHtmlText()).isNotBlank();

        // 3. 만약 해당 사이트에 JSON-LD가 있다면, "{" 로 시작하는 포맷이어야 함
        // (주의: 타겟 URL에 JSON-LD가 없는 사이트라면 이 검증은 빼거나 조건부로 처리해야 합니다)
        if (!result.jsonLd().isEmpty()) {
            assertThat(result.jsonLd().isArray()).isTrue();
        }
    }

    //Swagger 특성상 이스케이프('\') 문자가 내부 따옴표마다 있어야한다. 따라서, Swagger를 통한 테스트 전용 복붙 출력
    @Test
    @DisplayName("AI 서버 Swagger에 붙여넣을 payload 생성")
    void makePayloadForAiServerSwagger() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        String url = "https://www.apple.com/kr/shop/buy-iphone/iphone-17-pro";

        ScrapedData result = geoScrapingService.extractDataForAi(url, DomainStatus.ECOMMERCE);

        String jsonLdString = result.jsonLd().toString();

        // 2. 전체 요청 body 생성
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("url", url);
        payload.put("domain",DomainStatus.ECOMMERCE.toString());
        payload.put("html_text", result.refinedHtmlText().substring(0,3500));

        // 중요: set()이 아니라 put()을 써야 함. 왜냐하면, put()이 json_ld를 문자열로 넣기 때문이다.
        payload.put("json_ld", jsonLdString);

        // 3. Swagger에 붙여넣을 최종 JSON 출력
        String swaggerBody = objectMapper
                .writerWithDefaultPrettyPrinter()
                .writeValueAsString(payload);

        System.out.println(swaggerBody);
    }

    @Test
    @DisplayName("Jsoup 본문 추출이 부족하면 Playwright 폴백으로 동적 본문을 추출해야 한다.")
    void extractDataForAi_WhenStaticBodyInsufficient_ShouldFallbackToPlaywright() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/dynamic", exchange -> {
            String html = """
                    <!doctype html>
                    <html>
                    <head>
                      <meta charset="UTF-8" />
                      <title>dynamic test</title>
                      <script type="application/ld+json">
                      {"@context":"https://schema.org","@type":"Organization","name":"DynamicTest"}
                      </script>
                    </head>
                    <body>
                      <div id="app"></div>
                      <script>
                        setTimeout(function () {
                          document.getElementById('app').innerText =
                            'THIS_TEXT_IS_RENDERED_BY_BROWSER_RUNTIME_AND_SHOULD_TRIGGER_PLAYWRIGHT_FALLBACK_'
                            + 'THIS_TEXT_IS_RENDERED_BY_BROWSER_RUNTIME_AND_SHOULD_TRIGGER_PLAYWRIGHT_FALLBACK_';
                        }, 100);
                      </script>
                    </body>
                    </html>
                    """;

            byte[] response = html.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(200, response.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
        });
        server.start();

        try {
            String url = "http://localhost:" + server.getAddress().getPort() + "/dynamic";

            ScrapedData result = geoScrapingService.extractDataForAi(url, DomainStatus.ECOMMERCE);

            assertNotNull(result);
            assertThat(result.refinedHtmlText()).contains("THIS_TEXT_IS_RENDERED_BY_BROWSER_RUNTIME");
            assertThat(result.jsonLd().toString()).contains("\"@type\":\"Organization\"");
        } finally {
            server.stop(0);
        }
    }

    @ParameterizedTest(name = "본문 길이 {0}자 → 충분함={1}")
    @DisplayName("isSufficientBody는 50자 미만이면 무조건 부족으로 판단한다(300자 기준과 혼동하지 않는다).")
    @CsvSource({
            "0, false",
            "49, false",
            "50, true",
            "299, true",
            "300, true",
            "301, true",
    })
    void isSufficientBody_UsesFiftyCharHardBlockNotThreeHundred(int length, boolean expectedSufficient) {
        String body = "본".repeat(length);

        assertThat(geoScrapingService.isSufficientBody(body)).isEqualTo(expectedSufficient);
    }

    @Test
    @DisplayName("본문 전체가 'Loading...' 같은 shell placeholder면 50자 미만이 아니어도 부족으로 판단한다.")
    void isSufficientBody_TreatsWholeStringShellPlaceholderAsInsufficient() {
        assertThat(geoScrapingService.isSufficientBody("Loading...")).isFalse();
        assertThat(geoScrapingService.isSufficientBody("please wait...")).isFalse();
        assertThat(geoScrapingService.isSufficientBody("잠시만 기다려 주세요...")).isFalse();
    }

    @Test
    @DisplayName("실제 본문 안에 'loading'이라는 단어가 섞여 있을 뿐이면 shell로 오판하지 않는다.")
    void isSufficientBody_DoesNotFalsePositiveOnPartialMatch() {
        String realContent = "이 페이지는 loading indicator에 대한 설명을 담은 충분히 긴 실제 기사 본문입니다. ".repeat(3);

        assertThat(geoScrapingService.isSufficientBody(realContent)).isTrue();
    }

    @Test
    @DisplayName("정적 본문이 50자 이상이고 shell이 아니면 Playwright를 띄우지 않는다.")
    void extractDataForAi_WhenStaticBodySufficient_ShouldNotInvokePlaywright() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/sufficient", exchange -> {
            String html = "<!doctype html><html><body><article><p>"
                    + "이것은 충분히 긴 정적 본문입니다. ".repeat(10)
                    + "</p></article></body></html>";
            byte[] response = html.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(200, response.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
        });
        server.start();

        try {
            String url = "http://localhost:" + server.getAddress().getPort() + "/sufficient";
            GeoScrapingService spyService = spy(new GeoScrapingService());

            ScrapedData result = spyService.extractDataForAi(url, DomainStatus.ECOMMERCE);

            assertThat(result.refinedHtmlText()).contains("이것은 충분히 긴 정적 본문입니다");
            verify(spyService, never()).renderByPlaywright(anyString());
        } finally {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("정적 본문이 'Loading...' shell placeholder뿐이면 Playwright로 폴백해 렌더된 본문을 가져온다.")
    void extractDataForAi_WhenStaticBodyIsShellPlaceholder_ShouldFallbackToPlaywright() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/shell", exchange -> {
            String html = """
                    <!doctype html>
                    <html>
                    <body>
                      <div id="app">Loading...</div>
                      <script>
                        setTimeout(function () {
                          document.getElementById('app').innerText =
                            'SHELL_FALLBACK_TEXT_RENDERED_BY_BROWSER_RUNTIME_'
                            + 'SHELL_FALLBACK_TEXT_RENDERED_BY_BROWSER_RUNTIME_';
                        }, 100);
                      </script>
                    </body>
                    </html>
                    """;
            byte[] response = html.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(200, response.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
        });
        server.start();

        try {
            String url = "http://localhost:" + server.getAddress().getPort() + "/shell";

            ScrapedData result = geoScrapingService.extractDataForAi(url, DomainStatus.ECOMMERCE);

            assertNotNull(result);
            assertThat(result.refinedHtmlText()).contains("SHELL_FALLBACK_TEXT_RENDERED_BY_BROWSER_RUNTIME");
        } finally {
            server.stop(0);
        }
    }
}
