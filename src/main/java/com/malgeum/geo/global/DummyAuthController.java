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
        String token = jwtTokenProvider.generateToken("999",List.of("ROLE_USER"));
        return ResponseEntity.ok(token);
    }

    @GetMapping("/auth/secured")
    public ResponseEntity<String> secureApi(Authentication authentication){
        String clientId = authentication.getName();
        return ResponseEntity.ok("JWT 필터 통과 성공! 환영합니다, 고객사 ID: " + clientId);
    }
}
