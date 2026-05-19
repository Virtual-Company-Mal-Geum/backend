package com.malgeum.geo.domain.domain;

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

    @Column(name = "phone", nullable = false, length = 20)
    private String phone;
    
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

    public enum ClientStatus {
        ACTIVE, EXPIRED
    }

    public String updatePassword(String password){
        this.password=password;
        return password;
    }
}
