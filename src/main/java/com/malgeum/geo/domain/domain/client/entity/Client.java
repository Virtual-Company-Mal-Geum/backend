package com.malgeum.geo.domain.domain.client.entity;

import com.malgeum.geo.domain.BaseTimeEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "client")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class Client extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "email", nullable = false, length = 100, unique = true)
    private String email;

    @Column(name = "password", length = 255)
    private String password;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Column(name = "company", length = 100)
    private String company;

    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name="provider_id")
    private String providerId;
    
    @Enumerated(EnumType.STRING)
    private OAuthProvider provider;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ClientStatus status;

    @Builder
    public Client(String password, String name, String company, String email, String phone) {
        this.password = password;
        this.name = name;
        this.company = company;
        this.email = email;
        this.phone = phone;
        this.status = ClientStatus.ACTIVE;
    }

    @Builder
    public Client(String password, String name, String company, String email, String phone, String providerId, OAuthProvider provider) {
        this.password = password;
        this.name = name;
        this.company = company;
        this.email = email;
        this.phone = phone;
        this.providerId=providerId;
        this.provider=OAuthProvider.GOOGLE;
        this.status = ClientStatus.ACTIVE;
    }

    public enum ClientStatus {
        ACTIVE, EXPIRED
    }

    public enum OAuthProvider {
        GOOGLE
    }

    public String updatePassword(String password){
        this.password=password;
        return password;
    }

    public void linkOAuth(OAuthProvider provider, String providerId) {
        this.provider = provider;
        this.providerId = providerId;
    }
}
