package com.malgeum.geo.service;

import java.io.IOException;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class GeoScrapingService {
    
    public record ScrapedData(String htmlText, JsonNode jsonLd) {}
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
            String cleanHtmlText = limitTextLength(doc.body().text()); // html 본문 텍스트만 추가

            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode jsonLdNode = objectMapper.readTree(combinedJsonLd);
            return new ScrapedData(cleanHtmlText, jsonLdNode);
        } catch (IOException e) {
            // 접속 실패, 타임아웃, 404/403 에러 등의 경우
            log.error("[GEO Scraping] 정적 페이지 스크래핑 실패 - URL: {}, 원인: {}", url, e.getMessage());
            throw new RuntimeException("웹페이지 데이터를 가져올 수 없습니다.");
        }
    }

    //AI 서버의 토큰량 한계(4096)로 인해 본문 텍스트는 4000자로 제한. (그냥 앞에서 부터 자름.)
    //GeoAiService에서 AI 서버에게 전송할때 4000자로 제한하는데 로컬 환경에서도 동일하게 하려고 하는 메서드
    private String limitTextLength(String text){
        if(text.length()>4000){
            text = text.substring(0,4000);
        }
        return text;
    }
}
