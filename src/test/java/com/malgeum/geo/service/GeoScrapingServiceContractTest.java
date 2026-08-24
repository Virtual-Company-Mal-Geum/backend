package com.malgeum.geo.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.util.Iterator;
import java.util.List;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.malgeum.geo.domain.domain.order.entity.Order.DomainStatus;
import com.malgeum.geo.dto.GeoEvaluationRequest;
import com.malgeum.geo.dto.ScrapedData;

/**
 * 외부 URL 없이 고정 fixture만으로 AI 서버 4필드 계약(url/domain/html_text/json_ld)을 결정론적으로 검증한다.
 * 본문 markdown은 Java/Python 추출기 간 byte 단위 동일성을 주장하지 않고, 라벨·순서·구분자와
 * 필수 텍스트 보존만 검증한다.
 */
class GeoScrapingServiceContractTest {

    private static final String FIXTURE_DIR = "src/test/resources/geo/gateway-input/";

    private final GeoScrapingService geoScrapingService = new GeoScrapingService();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private Document loadFixtureHtml() throws Exception {
        File file = new File(FIXTURE_DIR + "static-page.html");
        return Jsoup.parse(file, "UTF-8", "https://example.com/geo-fixture/static-page.html");
    }

    private JsonNode loadExpected() throws Exception {
        return objectMapper.readTree(new File(FIXTURE_DIR + "expected-request.json"));
    }

    @Test
    @DisplayName("script[type=application/ld+json] 각각을 독립 파싱하고, 깨진 스크립트는 건너뛰며, 결과는 항상 배열이다.")
    void extractJsonLd_ParsesEachScriptIndependently() throws Exception {
        Document doc = loadFixtureHtml();

        ArrayNode jsonLd = geoScrapingService.extractJsonLd(doc);

        assertThat(jsonLd.isArray()).isTrue();
        assertThat(jsonLd).hasSize(2);
        assertThat(jsonLd.get(0).get("@type").asText()).isEqualTo("NewsArticle");
        assertThat(jsonLd.get(1).get("@type").asText()).isEqualTo("Person");
    }

    @Test
    @DisplayName("JSON-LD 스크립트가 없으면 json_ld는 빈 배열이다.")
    void extractJsonLd_ReturnsEmptyArrayWhenNoScripts() {
        Document doc = Jsoup.parse("<html><body><article>본문만 있습니다.</article></body></html>");

        ArrayNode jsonLd = geoScrapingService.extractJsonLd(doc);

        assertThat(jsonLd.isArray()).isTrue();
        assertThat(jsonLd).isEmpty();
    }

    @Test
    @DisplayName("JSON-LD @type이 domain보다 우선해 문서 유형을 결정한다.")
    void classifyDocumentType_PrefersJsonLdTypeOverDomain() throws Exception {
        Document doc = loadFixtureHtml();
        ArrayNode jsonLd = geoScrapingService.extractJsonLd(doc);

        String documentType = geoScrapingService.classifyDocumentType(DomainStatus.NEWS, jsonLd, doc.text());

        assertThat(documentType).isEqualTo("NEWS_ARTICLE");
    }

    @Test
    @DisplayName("html_text는 DOCUMENT_TYPE/TITLE/AUTHOR/PUBLISHED/PUBLISHER/MAIN_CONTENT 순서로, \\n\\n으로만 구분된다.")
    void buildHtmlText_ProducesLabelsInFixedOrderWithBlankLineSeparators() throws Exception {
        Document doc = loadFixtureHtml();
        JsonNode expected = loadExpected();
        String mainContent = geoScrapingService.toMarkDown(doc);

        String htmlText = geoScrapingService.buildHtmlText(doc, "NEWS_ARTICLE", mainContent);

        List<String> expectedOrder = objectMapper.convertValue(
                expected.get("htmlTextLabelOrder"), objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));

        String[] blocks = htmlText.split("\n\n");
        // 라벨과 값이 번갈아 나오므로 짝수 인덱스가 라벨이어야 한다.
        for (int i = 0; i < expectedOrder.size(); i++) {
            assertThat(blocks[i * 2]).isEqualTo("[" + expectedOrder.get(i) + "]");
        }
        assertThat(htmlText).startsWith("[DOCUMENT_TYPE]\n\nNEWS_ARTICLE");
        assertThat(htmlText).isEqualTo(htmlText.strip());
    }

    @Test
    @DisplayName("[MAIN_CONTENT]에는 제목/문단/목록/표/코드 텍스트가 보존되고 nav/footer/script는 남지 않는다.")
    void buildHtmlText_PreservesRequiredTextAndDropsBoilerplate() throws Exception {
        Document doc = loadFixtureHtml();
        JsonNode expected = loadExpected();
        String mainContent = geoScrapingService.toMarkDown(doc);

        String htmlText = geoScrapingService.buildHtmlText(doc, "NEWS_ARTICLE", mainContent);

        Iterator<JsonNode> mustContain = expected.get("mainContentMustContain").elements();
        while (mustContain.hasNext()) {
            assertThat(htmlText).contains(mustContain.next().asText());
        }
        Iterator<JsonNode> mustNotContain = expected.get("mainContentMustNotContain").elements();
        while (mustNotContain.hasNext()) {
            assertThat(htmlText).doesNotContain(mustNotContain.next().asText());
        }
    }

    @Test
    @DisplayName("직렬화된 요청의 최상위 키는 정확히 url/domain/html_text/json_ld 4개뿐이고 meta_tags/category가 없다.")
    void geoEvaluationRequest_SerializesToExactlyFourKeys() throws Exception {
        Document doc = loadFixtureHtml();
        String mainContent = geoScrapingService.toMarkDown(doc);
        ScrapedData scrapedData = geoScrapingService.buildScrapedData(
                "https://example.com/geo-fixture/static-page.html", DomainStatus.NEWS, doc, mainContent);

        GeoEvaluationRequest request = GeoEvaluationRequest.from(scrapedData);
        JsonNode serialized = objectMapper.valueToTree(request);

        List<String> keys = new java.util.ArrayList<>();
        serialized.fieldNames().forEachRemaining(keys::add);

        assertThat(keys).containsExactlyInAnyOrder("url", "domain", "html_text", "json_ld");
        assertThat(serialized.get("json_ld").isArray()).isTrue();
        assertThat(serialized.get("domain").asText()).isEqualTo("news");
    }
}
