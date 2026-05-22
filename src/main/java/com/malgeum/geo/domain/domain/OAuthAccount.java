package com.malgeum.geo.domain.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.NoArgsConstructor;

@Entity
@Table(name="oauth_account")
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class OAuthAccount {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name="email",nullable = false, length = 20,unique = true)
    private String email;

    @Column(name="provider",nullable = false)
    private Provider provider;

    @Column(name="provider_id",nullable = false)
    private Long providerId;

    public enum Provider {
        GOOGLE, KAKAO
    }

    public OAuthAccount(Client client){
        this.email = client.getEmail();
    }
}
