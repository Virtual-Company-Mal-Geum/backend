package com.malgeum.geo.domain.domain.order.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.malgeum.geo.domain.domain.order.entity.Order.DomainStatus;
import com.malgeum.geo.global.common.DataNotFoundException;

class GeoOrderRequestTest {

    private GeoOrderRequest requestWithServiceType(String serviceType) {
        return GeoOrderRequest.from("https://example.com", "site", serviceType);
    }

    @Test
    @DisplayName("쇼핑몰/뉴스/교육 키워드는 각각 ECOMMERCE/NEWS/EDUCATION으로 해석된다.")
    void resolvedDomainStatus_ResolvesKnownDomains() {
        assertThat(requestWithServiceType("쇼핑몰").resolvedDomainStatus()).isEqualTo(DomainStatus.ECOMMERCE);
        assertThat(requestWithServiceType("뉴스 매체").resolvedDomainStatus()).isEqualTo(DomainStatus.NEWS);
        assertThat(requestWithServiceType("교육 서비스").resolvedDomainStatus()).isEqualTo(DomainStatus.EDUCATION);
    }

    @Test
    @DisplayName("AI 서버가 서빙하지 않는 tech_blog(SaaS/테크/기술)는 신규 요청에서 더 이상 선택할 수 없다.")
    void resolvedDomainStatus_RejectsTechBlogKeywords() {
        assertThatThrownBy(() -> requestWithServiceType("SaaS").resolvedDomainStatus())
                .isInstanceOf(DataNotFoundException.class);
        assertThatThrownBy(() -> requestWithServiceType("테크 블로그").resolvedDomainStatus())
                .isInstanceOf(DataNotFoundException.class);
        assertThatThrownBy(() -> requestWithServiceType("기술 블로그").resolvedDomainStatus())
                .isInstanceOf(DataNotFoundException.class);
    }

    @Test
    @DisplayName("알 수 없는 serviceType은 여전히 오류로 처리된다.")
    void resolvedDomainStatus_RejectsUnknownServiceType() {
        assertThatThrownBy(() -> requestWithServiceType("알 수 없는 업종").resolvedDomainStatus())
                .isInstanceOf(DataNotFoundException.class);
    }
}
