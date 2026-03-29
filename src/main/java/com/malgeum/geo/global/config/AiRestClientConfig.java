package com.malgeum.geo.global.config;

import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;

@Configuration
public class AiRestClientConfig {
    @SuppressWarnings("null")
    @Bean
    public RestClientCustomizer aRestClientCustomizer() {
        return builder -> {
                //Java11부터는 기본 connectionTimeout에 대해 HttpClient로만 직접 설정해야합니다. JdkClientHttpRequestFactory로는 불가능.
                HttpClient httpClient = HttpClient.newBuilder()
                                                .connectTimeout(Duration.ofSeconds(5))
                                                .build();

                // [의도] AI 모델의 추론 연산은 VRAM 상태와 입력 길이에 따라 5초 ~ 15초 이상 소요될 수 있습니다.
                // Spring의 기본 Read Timeout(보통 무한대이거나 너무 짧음)에 의존하지 않고, 명시적으로 60초의 넉넉한 대기 시간을 설정하여 커넥션 끊김을 방지합니다.
                JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
                requestFactory.setReadTimeout(Duration.ofSeconds(60));
                builder.requestFactory(requestFactory);
        };
    }
}
