package com.malgeum.geo.service;

import java.io.IOException;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.malgeum.geo.domain.domain.order.entity.Order.DomainStatus;
import com.malgeum.geo.dto.ScrapedData;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.WaitUntilState;
import com.vladsch.flexmark.html2md.converter.FlexmarkHtmlConverter;
import com.vladsch.flexmark.util.data.MutableDataSet;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class GeoScrapingService {

    private static final Set<String> DOCUMENT_TYPE_BY_JSONLD = Set.of(
            "newsarticle", "article", "blogposting", "course", "product");

    private static final Set<String> EDU_ORG_JSONLD_TYPES = Set.of(
            "educationalorganization", "school", "collegeoruniversity",
            "highschool", "middleschool", "elementaryschool", "preschool");

    private static final Pattern EDU_ORG_KEYWORDS = Pattern.compile("학원|어학원|아카데미|교습소|공부방|학습관");
    private static final Pattern COURSE_KEYWORDS = Pattern.compile("커리큘럼|수강 대상|강의 소개|course curriculum");

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final FlexmarkHtmlConverter htmlToMarkdown = FlexmarkHtmlConverter.builder(
            new MutableDataSet().set(FlexmarkHtmlConverter.SETEXT_HEADINGS, false)).build();

    /**
     * 타겟 URL에서 HTML을 긁어와 AI 서버용 4필드(url/domain/html_text/json_ld) 원본 데이터를 만듭니다.
     *
     * @param url 프론트엔드로부터 입력받은 타겟 URL
     * @return html_text가 라벨 형식으로 조립된 ScrapedData
     */
    public ScrapedData extractDataForAi(String url, DomainStatus domainStatus) {
        try {
            log.info("[Geo Scraping] Jsoup 스크래핑 시작 - URL: {}", url);
            Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0")
                    .timeout(5000)
                    .get();
            String mainContent = toMarkDown(doc);
            if (hasMeaningfulBody(mainContent)) {
                return buildScrapedData(url, domainStatus, doc, mainContent);
            }
            log.warn("[Geo Scraping] Jsoup 결과 본문이 비어 Playwright로 폴백 - URL: {}", url);
        } catch (Exception jsoupError) {
            log.warn("[Geo Scraping] Jsoup 실패, Playwright로 폴백 - URL: {}, 원인: {}", url, jsoupError.getMessage());
        }

        try {
            Document doc = renderByPlaywright(url);
            String mainContent = toMarkDown(doc);
            if (!hasMeaningfulBody(mainContent)) {
                throw new RuntimeException("본문을 추출할 수 없습니다.");
            }
            return buildScrapedData(url, domainStatus, doc, mainContent);
        } catch (RuntimeException alreadyClassified) {
            throw alreadyClassified;
        } catch (Exception playwrightError) {
            log.error("[Geo Scraping] Playwright 포함 스크래핑 최종 실패 - URL: {}, 원인: {}",
                    url, playwrightError.getMessage());
            throw new RuntimeException("웹페이지 데이터를 가져올 수 없습니다.");
        }
    }

    private Document renderByPlaywright(String url) {
        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
            Page page = browser.newPage();
            page.navigate(url,
                    new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED).setTimeout(15000));
            page.waitForTimeout(1500);
            String html = page.content();
            browser.close();
            return Jsoup.parse(html, url);
        }
    }

    ScrapedData buildScrapedData(String url, DomainStatus domainStatus, Document doc, String mainContent) {
        ArrayNode jsonLd = extractJsonLd(doc);
        String documentType = classifyDocumentType(domainStatus, jsonLd, doc.text());
        String htmlText = buildHtmlText(doc, documentType, mainContent);
        return new ScrapedData(url, domainStatus.toString(), htmlText, jsonLd);
    }

    private boolean hasMeaningfulBody(String htmlText) {
        return htmlText != null && !htmlText.isBlank();
    }

    /**
     * script[type=application/ld+json] 각각을 독립적으로 파싱합니다. 깨진 스크립트 하나 때문에
     * 정상 스크립트까지 버리지 않으며, 결과가 없으면 빈 ArrayNode를 반환합니다.
     */
    ArrayNode extractJsonLd(Document doc) {
        ArrayNode result = objectMapper.createArrayNode();
        for (Element script : doc.select("script[type=application/ld+json]")) {
            JsonNode parsed;
            try {
                parsed = objectMapper.readTree(script.data());
            } catch (Exception e) {
                log.warn("[Geo Scraping] JSON-LD 스크립트 파싱 실패, 건너뜀 - URL: {}, 원인: {}",
                        doc.location(), e.getMessage());
                continue;
            }
            if (parsed.isObject()) {
                result.add(parsed);
            } else if (parsed.isArray()) {
                parsed.forEach(element -> {
                    if (element.isObject()) {
                        result.add(element);
                    }
                });
            }
        }
        return result;
    }

    /**
     * json_ld의 @type을 우선 사용하고, 없으면 domain으로 폴백해 AI 서버의 document_type 분류 규칙을 따릅니다.
     */
    String classifyDocumentType(DomainStatus domain, JsonNode jsonLd, String visibleText) {
        Set<String> schemaTypes = new TreeSet<>();
        collectJsonLdTypes(jsonLd, schemaTypes);

        for (String schemaType : schemaTypes) {
            if (DOCUMENT_TYPE_BY_JSONLD.contains(schemaType)) {
                return switch (schemaType) {
                    case "newsarticle" -> "NEWS_ARTICLE";
                    case "article" -> "ARTICLE";
                    case "blogposting" -> "BLOG_ARTICLE";
                    case "course" -> "COURSE";
                    case "product" -> "PRODUCT";
                    default -> "WEB_PAGE";
                };
            }
        }

        String text = visibleText == null ? "" : visibleText.toLowerCase(Locale.KOREAN);
        return switch (domain) {
            case NEWS -> "NEWS_ARTICLE";
            case ECOMMERCE -> "PRODUCT";
            case TECHBLOG -> "TECH_BLOG";
            case EDUCATION -> classifyEducation(schemaTypes, text);
        };
    }

    private String classifyEducation(Set<String> schemaTypes, String visibleTextLower) {
        boolean isEduOrgByJsonLd = schemaTypes.stream().anyMatch(EDU_ORG_JSONLD_TYPES::contains);
        if (isEduOrgByJsonLd || EDU_ORG_KEYWORDS.matcher(visibleTextLower).find()) {
            return "EDUCATIONAL_ORGANIZATION";
        }
        if (COURSE_KEYWORDS.matcher(visibleTextLower).find()) {
            return "COURSE";
        }
        return "EDUCATION_ARTICLE";
    }

    private void collectJsonLdTypes(JsonNode node, Set<String> types) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isObject()) {
            JsonNode typeNode = node.get("@type");
            if (typeNode != null) {
                if (typeNode.isArray()) {
                    typeNode.forEach(t -> {
                        if (t.isTextual()) {
                            types.add(t.asText().toLowerCase(Locale.ROOT));
                        }
                    });
                } else if (typeNode.isTextual()) {
                    types.add(typeNode.asText().toLowerCase(Locale.ROOT));
                }
            }
            node.fields().forEachRemaining(entry -> collectJsonLdTypes(entry.getValue(), types));
        } else if (node.isArray()) {
            node.forEach(child -> collectJsonLdTypes(child, types));
        }
    }

    /**
     * [DOCUMENT_TYPE] → [TITLE] → [AUTHOR] → [PUBLISHED] → [PUBLISHER] → [MAIN_CONTENT] 순서로
     *      * 비어있지 않은 섹션만 "\n\n"으로 이어붙입니다. JSON-LD 값은 여기 주입하지 않습니다(렌더된 HTML만 사용).
     */
    String buildHtmlText(Document doc, String documentType, String mainContent) {
        StringBuilder builder = new StringBuilder();
        appendSection(builder, "DOCUMENT_TYPE", documentType);
        appendSection(builder, "TITLE", extractTitle(doc));
        appendSection(builder, "AUTHOR", extractMeta(doc, "meta[name=author]"));
        appendSection(builder, "PUBLISHED", firstNonBlank(
                extractMeta(doc, "meta[property=article:published_time]"),
                extractMeta(doc, "meta[name=date]")));
        appendSection(builder, "PUBLISHER", extractMeta(doc, "meta[property=og:site_name]"));
        appendSection(builder, "MAIN_CONTENT", mainContent);
        return builder.toString().strip();
    }

    private void appendSection(StringBuilder builder, String label, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        if (builder.length() > 0) {
            builder.append("\n\n");
        }
        builder.append('[').append(label).append(']').append("\n\n").append(value.strip());
    }

    private String extractTitle(Document doc) {
        return firstNonBlank(
                extractMeta(doc, "meta[property=og:title]"),
                extractMeta(doc, "meta[name=twitter:title]"),
                doc.title());
    }

    private String extractMeta(Document doc, String cssQuery) {
        Element meta = doc.selectFirst(cssQuery);
        return meta == null ? "" : meta.attr("content");
    }

    private String firstNonBlank(String... candidates) {
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank()) {
                return candidate;
            }
        }
        return "";
    }

    String toMarkDown(Document doc) {
        Element root = doc.selectFirst("article");
        if (root == null)
            root = doc.selectFirst("main");
        if (root == null)
            root = doc.body();
        if (root == null)
            return "";

        Element clean = root.clone();
        clean.select("script, style, noscript, iframe, svg").remove();

        return htmlToMarkdown.convert(clean).strip();
    }
}
