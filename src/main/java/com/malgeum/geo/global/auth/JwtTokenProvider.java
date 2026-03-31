package com.malgeum.geo.global.auth;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import javax.crypto.SecretKey;

//User id 기반 JWT 토큰 발급
@Slf4j
@Component
public class JwtTokenProvider {
    private final SecretKey key;

    public JwtTokenProvider(@Value("${jwt.secret}") String key){
        byte[] keyByte = key.getBytes();
        this.key = Keys.hmacShaKeyFor(keyByte);
    }

    // 1. 유저 정보를 가지고 AccessToken을 생성하는 메서드
    public String generateToken(String userUid, List<String> roles) {
        Claims claims = (Claims) Jwts.claims().subject(userUid).add("auth", roles).build();

        long now = (new Date()).getTime();
        Date accessTokenExpiresIn = new Date(now + 86400000); // 1일동안만 토큰 유효

        return Jwts.builder()
            .claims(claims)
            .expiration(accessTokenExpiresIn)
            .signWith(key) 
            .compact();
    }

    // 2. JWT 토큰을 복호화하여 토큰에 들어있는 정보를 꺼내는 메서드
    public Authentication getAuthentication(String accessToken) {
        Claims claims = parseClaims(accessToken);

        if (claims.get("auth") == null) {
            throw new RuntimeException("권한 정보가 없는 토큰입니다.");
        }

        Collection<? extends GrantedAuthority> authorities =
                Arrays.stream(claims.get("auth").toString().split(","))
                        .map(SimpleGrantedAuthority::new)
                        .collect(Collectors.toList());
        
        // 여기서 User는 우리가 만든 User 엔티티가 아닌, Spring Security가 제공하는 UserDetails 구현체
        UserDetails principal = new User(claims.getSubject(), "", authorities);
        return new UsernamePasswordAuthenticationToken(principal, "", authorities);
    }

    // 3. 토큰 정보를 검증하는 메서드
    public boolean validateToken(String token) {
        try {
            Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
            return true;
        } catch (io.jsonwebtoken.security.SecurityException | MalformedJwtException e) {
            log.info("Invalid JWT Token", e);
        } catch (ExpiredJwtException e) {
            log.info("Expired JWT Token", e);
        } catch (UnsupportedJwtException e) {
            log.info("Unsupported JWT Token", e);
        } catch (IllegalArgumentException e) {
            log.info("JWT claims string is empty.", e);
        }
        return false;
    }

    private Claims parseClaims(String accessToken) {
        try {
            return Jwts.parser().verifyWith(key).build().parseSignedClaims(accessToken).getPayload();
        } catch (ExpiredJwtException e) {
            return e.getClaims();
        }
    }
}
