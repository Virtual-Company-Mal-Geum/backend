package com.malgeum.geo.domain.domain;

import com.malgeum.geo.domain.BaseTimeEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

    @Column(name = "register_id", nullable = false, length = 50, unique = true)
    private String registerId;

    @Column(name = "password", length = 255)
    private String password;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Column(name = "company", nullable = false, length = 100)
    private String company;

    @Column(name = "email", nullable = false, length = 100)
    private String email;

    @Column(name = "phone", nullable = false, length = 20)
    private String phone;

    @Column(name = "status", nullable = false, length = 20)
    private ClientStatus status;

    @Builder
    public Client(String registerId, String password, String name, String company, String email, String phone) {
        this.registerId = registerId;
        this.password = password;
        this.name = name;
        this.company = company;
        this.email = email;
        this.phone = phone;
        this.status = ClientStatus.ACTIVE;
    }

    @Builder
    public Client(String password, String name, String company, String email, String phone){
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

}
