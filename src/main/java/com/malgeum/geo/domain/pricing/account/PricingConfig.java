package com.malgeum.geo.domain.pricing.account;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PricingConfig { //오직 대한민국 및 일본 지역에서만(동일시차)
    public static final ZoneId BILLING_ZONE = ZoneId.of("Asia/Seoul");

    @Bean
    public Clock pricingClock() {
        return Clock.system(BILLING_ZONE);
    }
}
