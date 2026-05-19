package com.malgeum.geo.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.SecurityContextRepository;

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
            // CSRF 비활성화 (JWT를 쓰기 때문에 필요 없음)
            .csrf(csrf -> csrf.disable())
            // 폼 로그인 비활성화
            .formLogin(formLogin -> formLogin
                                        .loginPage("/api/v1/geo/login")
                                        .defaultSuccessUrl("/")
            )
            // 3. URL별 인가 규칙 설정 (로그인은 누구나 가능하지만, 나머지 요청들은 모두 인가(jwt)가 필요함)
            .authorizeHttpRequests(auth -> auth
				.requestMatchers("/api/v1/geo/**").permitAll()
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
}
