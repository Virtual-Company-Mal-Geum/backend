package com.malgeum.geo.domain.domain;

import java.util.UUID;

import com.malgeum.geo.domain.BaseTimeEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "client_order")
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class Order extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY) // 실무에서 N+1 문제를 막기 위한 절대 규칙: 무조건 LAZY!
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    @Column(name = "target_url", nullable = false, length = 2048)
    private String targetUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "categoryStatus", nullable = false)
    private CategoryStatus categoryStatus;

    @Column(name = "resource_key", nullable = false, length = 36, unique = true)
    private String resourceKey; // Java의 UUID를 String으로 변환해서 저장

    @Enumerated(EnumType.STRING)
    @Column(name = "job_status", nullable = false, length = 20)
    private JobStatus jobStatus;

    // 결제 수단과 상태는 미정
    // private PaymentStatus paymentStatus;
    // private PaymentMeans paymentMeans;

    @Builder
    public Order(Client client, String targetUrl, CategoryStatus categoryStatus) {
        this.client = client;
        this.targetUrl = targetUrl;
        this.resourceKey = UUID.randomUUID().toString();
        this.jobStatus = JobStatus.PENDING;
        this.categoryStatus = CategoryStatus.ETC;
    }

    // 상태 변경을 위한 의미 있는 비즈니스 메서드 (단순 Setter 지양)
    public void updateStatus(JobStatus newStatus) {
        this.jobStatus = newStatus;
    }

    public enum JobStatus {
        // 대기중, 처리중, 성공, 실패
        PENDING, PROCESSING, SUCCESS, FAILED
    }

    public enum CategoryStatus{
        //뉴스, 이커머스(쇼핑), 교육, 기술 블로그, 기타
        NEWS, ECOMMERCE, EDUCATION, TECHBLOG, ETC
    }
}
