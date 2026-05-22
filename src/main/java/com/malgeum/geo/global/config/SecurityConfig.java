package com.malgeum.geo.global.config;

import java.net.http.HttpRequest;
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

import com.malgeum.geo.global.filter.JwtAuthenticationFilter;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    //private final SecurityContextRepository securityContextRepository;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // SaaS API 서버이므로 세션 정책을 STATELESS로 설정
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // 쿠키 기반 세션 로그인에서 중요한 보안 기능인 CSRF 비활성화 (JWT를 쓰기 때문에 필요 없음)
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            // 폼 로그인 비활성화
            .formLogin(formLogin -> formLogin
                                        .loginPage("/api/v1/geo/login")
                                        .defaultSuccessUrl("/")
            )
            // 3. URL별 인가 규칙 설정 (로그인은 누구나 가능하지만, 나머지 요청들은 모두 인가(jwt)가 필요함)
            .authorizeHttpRequests(auth -> auth
				.requestMatchers(HttpMethod.OPTIONS,"/**").permitAll()
                .requestMatchers("/api/v1/geo/login").permitAll()
                .requestMatchers("/api/v1/geo/signup").permitAll()
                .anyRequest().authenticated()
            )

            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint((request, response, authException) -> 
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized"))
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    } 
    
    @Bean
    public PasswordEncoder passwordEncoder(){
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        // 이용하려는 Origins가 있다면, 어떤 것을 허가 해줄지?
        config.setAllowedOrigins(List.of(
                "http://localhost:5173",
                "http://127.0.0.1:5173",
                "http://localhost:5500",
                "http://127.0.0.1:5500",
                "http://localhost:3000",
                "http://127.0.0.1:3000"
        ));

        // 요청 메시지에서 어떤 HTTP METHOD만 허용할지?
        config.setAllowedMethods(List.of(
                "GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"
        ));
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
