package com.malgeum.geo.global.auth;

import java.io.IOException;
import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import com.malgeum.geo.domain.domain.Client;
import com.malgeum.geo.global.common.ClientRepository;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class OAuth2LoginSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final JwtTokenProvider jwtTokenProvider;
    private final ClientRepository clientRepository;

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException {

        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();

        String email = oAuth2User.getAttribute("email");

        Client client = clientRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("OAuth 로그인 사용자를 찾을 수 없습니다."));

        String accessToken = jwtTokenProvider.generateToken(client.getEmail(), List.of("ROLE_USER"));

        String redirectUrl = "http://localhost:5173/geo-index.html?accessToken=" + accessToken;

        getRedirectStrategy().sendRedirect(request, response, redirectUrl);
    }
}