package com.malgeum.geo.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "spring.security.oauth2.client.provider")
public class OAuth2ProviderProperties {
    public record ProviderConfig(
            String authorizationUri,
            String tokenUri,
            String userInfoUri,
            String userNameAttribute) {
    }
}
