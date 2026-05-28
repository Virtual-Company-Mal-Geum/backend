package com.malgeum.geo.service;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import com.malgeum.geo.domain.domain.Order.DomainStatus;
import com.malgeum.geo.dto.ScrapedData;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.WaitUntilState;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class GeoScrapingService {

    /**
     * 타겟 URL에서 정적 HTML을 긁어와 JSON-LD 데이터만 추출합니다.
     * 
     * @param url 프론트엔드로부터 입력받은 타겟 URL
     * @return 추출된 JSON-LD 문자열 리스트 (한 페이지에 여러 개가 있을 수 있음)
     */
    public ScrapedData extractDataForAi(String url, DomainStatus domainStatus) {
        try {
            log.info("[Geo Scraping] Jsoup 스크래핑 시작 - URL: {}", url);
            ScrapedData jsoupResult = scrapeByJsoup(url, domainStatus);

            if (hasMeaningfulBody(jsoupResult.htmlText())) {
                return jsoupResult;
            }

            log.warn("[Geo Scraping] Jsoup 결과 본문이 비어 Playwright로 폴백 - URL: {}", url);
            return scrapeByPlaywright(url, domainStatus);
        } catch (Exception jsoupError) {
            log.warn("[Geo Scraping] Jsoup 실패, Playwright로 폴백 - URL: {}, 원인: {}", url, jsoupError.getMessage());
            try {
                return scrapeByPlaywright(url, domainStatus);
            } catch (Exception playwrightError) {
                log.error("[Geo Scraping] Playwright 포함 스크래핑 최종 실패 - URL: {}, 원인: {}",
                        url, playwrightError.getMessage());
                throw new RuntimeException("웹페이지 데이터를 가져올 수 없습니다.");
            }
        }
    }

    private ScrapedData scrapeByJsoup(String url, DomainStatus domainStatus) throws IOException {
        Document doc = Jsoup.connect(url)
                .userAgent("Mozilla/5.0")
                .timeout(5000)
                .get();
        return buildScrapedData(url, domainStatus, doc);
    }

    private ScrapedData scrapeByPlaywright(String url, DomainStatus domainStatus) {
        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
            Page page = browser.newPage();
            page.navigate(url,
                    new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED).setTimeout(15000));
            page.waitForTimeout(1500);
            String html = page.content();
            browser.close();

            Document doc = Jsoup.parse(html, url);
            return buildScrapedData(url, domainStatus, doc);
        }
    }

    private ScrapedData buildScrapedData(String url, DomainStatus domainStatus, Document doc) {
        Elements scriptTags = doc.select("script[type=application/ld+json]");
        String combinedJsonLd = scriptTags.stream()
                .map(Element::data)
                .collect(Collectors.joining(", ", "[", "]"));

        doc.select("header, nav, footer, script, style, noscript, iframe, svg").remove();
        Map<String, String> cleanMetaTags = new HashMap<>();
        for (Element meta : doc.select("meta")) {
            String key = meta.attr("name");
            if (key.isEmpty())
                key = meta.attr("property"); // og:, twitter: 등
            String content = meta.attr("content");
            if (!key.isEmpty() && !content.isEmpty()) {
                cleanMetaTags.put(key, content);
            }
        }
        String cleanHtmlText = limitTextLength(doc.body() == null ? "" : doc.body().text());
        return new ScrapedData(url, domainStatus.toString(), cleanHtmlText, combinedJsonLd, cleanMetaTags);
    }

    private boolean hasMeaningfulBody(String htmlText) {
        return htmlText != null && !htmlText.isBlank() && htmlText.length() >= 100;
    }

    // AI 서버의 토큰량 한계(4096)로 인해 본문 텍스트는 3500자로 제한. (그냥 앞에서 부터 자름.)
    // GeoAiService에서 AI 서버에게 전송할때 3500자로 제한하는데 로컬 환경에서도 동일하게 하려고 하는 메서드
    private String limitTextLength(String text) {
        final int maxLength = 3500;
        if (text.length() > maxLength) {
            text = text.substring(0, maxLength);
        }
        return text;
    }
}
