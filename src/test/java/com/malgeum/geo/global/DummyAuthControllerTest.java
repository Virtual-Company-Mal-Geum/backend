package com.malgeum.geo.global;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.malgeum.geo.global.auth.JwtTokenProvider;
import com.malgeum.geo.global.config.SecurityConfig;
import com.malgeum.geo.global.filter.JwtAuthenticationFilter;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;

//JWT 및 SecurityConfig Mock 테스트
@WebMvcTest(DummyAuthController.class)
@Import({ SecurityConfig.class, JwtAuthenticationFilter.class })
public class DummyAuthControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JpaMetamodelMappingContext mockJpaMetamodelMappingContext;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @Test
    @DisplayName("JWT 토큰 없이 보안 API에 접근하면 401(또는 403) 에러가 나야 한다.")
    void accessSecuredApiWithoutToken_ShouldFail() throws Exception {
        // given: 텅 빈 헤더 (토큰 없음)

        // when & then: 가짜 Postman으로 GET 요청을 날리고 결과를 검증합니다.
        mockMvc.perform(get("/api/v1/test/secured"))
                .andExpect(status().isUnauthorized()); // 401 코드가 떨어지는지 기대(Expect)함
    }

    @Test
    @DisplayName("가짜 로그인 API를 호출하면 임시 JWT 토큰을 발급 받아야한다.")
    void dummy_fake_login() throws Exception {
        // given: 가짜 JwtTokenProvider가 "1"라는 아이디를 받으면 "fake-jwt-token"을 주도록
        // 조작(Stubbing)합니다.
        given(jwtTokenProvider.generateToken("1", List.of("ROLE_USER"))).willReturn("fake-jwt-token");

        // when & then: 가짜 Postman으로 POST 요청을 날리고 결과를 검증합니다.
        mockMvc.perform(post("/api/v1/auth/dummy-login"))
                .andExpect(status().isOk())
                .andExpect(content().string("fake-jwt-token"));
    }

    @Test
    @DisplayName("유효한 JWT 토큰을 헤더에 넣으면 보안 API 통과에 성공해야 한다.")
    void accessSecuredApiWithValidToken_ShouldSucceed() throws Exception {
        // given: 가짜 토큰을 준비하고, 필터가 이 토큰을 검증할 때 무조건 '통과(true)'시키도록 조작합니다.
        String validToken = "valid-fake-token";
        given(jwtTokenProvider.validateToken(validToken)).willReturn(true);
        // 토큰에서 아이디를 꺼낼 때는 "1"를 반환하도록 조작합니다.
        given(jwtTokenProvider.getAuthentication(validToken))
                .willReturn(new UsernamePasswordAuthenticationToken("1", "", List.of()));

        // when & then: 헤더에 토큰을 싣고 요청을 쏩니다!
        mockMvc.perform(get("/api/v1/auth/secured")
                .header("Authorization", "Bearer " + validToken))
                .andExpect(status().isOk())
                .andExpect(content().string("JWT 필터 통과 성공! 환영합니다, 고객사 ID: 1"));
    }
}
