package com.malgeum.geo.global;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.malgeum.geo.global.auth.JwtTokenProvider;

// 로그인 기능 테스트를 위한 가짜 Controller
@RestController
@RequestMapping("/api/v1")
public class DummyAuthController {
    private final JwtTokenProvider jwtTokenProvider;

    public DummyAuthController(JwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @PostMapping("/auth/dummy-login")
    public ResponseEntity<String> testLogin() {
        // 1L로 생성된 테스트 고객의 ID를 문자열로 변환하여 JWT 토큰 생성
        // TODO: 실제로는 고객의 ID와 권한 정보를 DB에서 조회하여 토큰에 반영해야 합니다.
        String token = jwtTokenProvider.generateToken("1", List.of("ROLE_USER"));
        return ResponseEntity.ok(token);
    }

    @GetMapping("/auth/secured")
    public ResponseEntity<String> secureApi(Authentication authentication) {
        String clientId = authentication.getName();
        return ResponseEntity.ok("JWT 필터 통과 성공! 환영합니다, 고객사 ID: " + clientId);
    }
}
