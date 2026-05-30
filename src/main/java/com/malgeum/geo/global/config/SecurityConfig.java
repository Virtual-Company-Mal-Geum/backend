package com.malgeum.geo.global.config;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.malgeum.geo.global.auth.OAuth2LoginSuccessHandler;
import com.malgeum.geo.global.filter.JwtAuthenticationFilter;
import com.malgeum.geo.service.CustomOAuth2UserService;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {
        private final JwtAuthenticationFilter jwtAuthenticationFilter;
        private final OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;
        // private final SecurityContextRepository securityContextRepository;

        @Bean
        public SecurityFilterChain filterChain(HttpSecurity http, CustomOAuth2UserService customOAuth2UserService)
                        throws Exception {
                http
                                // OAuth2 인가코드 플로우(state 저장)와 공존하도록 필요 시에만 세션 생성
                                .sessionManagement(session -> session
                                                .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                                // 쿠키 기반 세션 로그인에서 중요한 보안 기능인 CSRF 비활성화 (JWT를 쓰기 때문에 필요 없음)
                                .csrf(csrf -> csrf.disable())
                                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                                .formLogin(formLogin -> formLogin
                                                .loginPage("/api/v1/geo/login")
                                                .defaultSuccessUrl("/"))
                                .logout(logout -> logout
                                                .logoutUrl("/api/v1/geo/logout")
                                                .logoutSuccessUrl("/api/v1/geo/")
                                                .invalidateHttpSession(true))
                                .oauth2Login(oauth2 -> oauth2
                                                .userInfoEndpoint(userInfo -> userInfo
                                                                .userService(customOAuth2UserService))
                                                .successHandler(oAuth2LoginSuccessHandler)
                                                .failureHandler((request, response, exception) -> {
                                                        response.sendError(HttpServletResponse.SC_UNAUTHORIZED,
                                                                        "OAuth2 login failed: "
                                                                                        + exception.getMessage());
                                                }))
                                // 3. URL별 인가 규칙 설정 (로그인은 누구나 가능하지만, 나머지 요청들은 모두 인가(jwt)가 필요함)
                                .authorizeHttpRequests(auth -> auth
                                                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                                                .requestMatchers(
                                                                "/login",
                                                                "/signup",
                                                                "/error",
                                                                "/oauth2/**",
                                                                "/login/oauth2/**",
                                                                "/api/v1/geo/login",
                                                                "/api/v1/geo/signup")
                                                .permitAll()
                                                .anyRequest().authenticated())
                                .exceptionHandling(exceptions -> exceptions
                                                .authenticationEntryPoint((request, response, authException) -> response
                                                                .sendError(HttpServletResponse.SC_UNAUTHORIZED,
                                                                                "Unauthorized")))
                                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

                return http.build();
        }

        @Bean
        public PasswordEncoder passwordEncoder() {
                return PasswordEncoderFactories.createDelegatingPasswordEncoder();
        }

        @Bean
        public CorsConfigurationSource corsConfigurationSource() {
                CorsConfiguration config = new CorsConfiguration();

                // 이용하려는 Origins가 있다면, 어떤 것을 허가 해줄지?
                config.setAllowedOrigins(List.of(
                                "http://localhost:5500",
                                "http://127.0.0.1:5500",
                                "http://localhost:3000",
                                "http://127.0.0.1:3000",
                                "http://localhost:5173",
                                "http://127.0.0.1:5173"));

                // 요청 메시지에서 어떤 HTTP METHOD만 허용할지?
                config.setAllowedMethods(List.of(
                                "GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
                // 어떤 요청 헤더를 허용할지?
                config.setAllowedHeaders(List.of("*"));
                // JWT 요청을 받을때 어떤 상태일 때 허용할지?
                config.setExposedHeaders(List.of("Authorization"));

                // 현재 fetch에 credentials 옵션이 없으므로 false로 두는 게 안전함
                // 쿠키 및 세션 기반 인증을 해야하게 되면 true 필요
                config.setAllowCredentials(false);

                UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
                source.registerCorsConfiguration("/**", config);

                return source;
        }
}
