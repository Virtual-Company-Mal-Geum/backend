package com.malgeum.geo.service;

import java.io.IOException;
import java.util.stream.Collectors;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class GeoScrapingService {
    
    public record ScrapedData(String htmlText, String jsonLd) {}
    /**
     * 타겟 URL에서 정적 HTML을 긁어와 JSON-LD 데이터만 추출합니다.
     * @param url 프론트엔드로부터 입력받은 타겟 URL
     * @return 추출된 JSON-LD 문자열 리스트 (한 페이지에 여러 개가 있을 수 있음)
     */
    public ScrapedData extractDataForAi(String url) {
        // [DTO] 스크래핑 결과를 담을 내부 Record
        try{
            log.info("[Geo Scraping] Jsoup 스크래핑 시작 - URL: {}",url);

            //Document - Connect 이후 받은 HTML 전체 문서
            //Elements - Element가 모인 자료형
            //Element - Document의 HTML 요소
            Document doc = Jsoup.connect(url)
                                // 일반 브라우저처럼 위장하여 봇 차단(403) 방어
                                .userAgent("Mozilla/5.0") 
                                .timeout(5000) // 5초 타임아웃
                                .get();
            // 1. JSON-LD 추출 및 하나의 문자열로 결합
            Elements scriptTags = doc.select("script[type=application/ld+json]");
            String combinedJsonLd = scriptTags.stream()
                                            .map(Element::data)
                                            .collect(Collectors.joining(", ", "[","]")); // 여러 개의 JSON-LD를 배열 형태로 묶기
            // 2. 불필요한 태그(GNB, Footer, Script 등) 싹 제거하여 순수 본문만 남기기
            doc.select("header, nav, footer, script, style, noscript").remove();
            String cleanHtmlText = doc.body().text(); // html 본문 텍스트만 추가

            return new ScrapedData(cleanHtmlText, combinedJsonLd);
        } catch (IOException e) {
            // 접속 실패, 타임아웃, 404/403 에러 등의 경우
            log.error("[GEO Scraping] 정적 페이지 스크래핑 실패 - URL: {}, 원인: {}", url, e.getMessage());
            throw new RuntimeException("웹페이지 데이터를 가져올 수 없습니다.");
        }
    }
}
