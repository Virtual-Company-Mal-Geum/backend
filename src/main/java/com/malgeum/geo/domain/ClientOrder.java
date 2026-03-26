package com.malgeum.geo.domain;

import java.net.URL;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "client_order")
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class ClientOrder extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY) // 실무에서 N+1 문제를 막기 위한 절대 규칙: 무조건 LAZY!
    @Column(name = "client_id", nullable = false)
    private Client client;

    @Column(name = "target_url", nullable = false, length = 2048)
    private URL targetUrl;

    @Column(name = "uuid", nullable = false, length = 36, unique = true)
    private String uuid; // Java의 UUID를 String으로 변환해서 저장

    @Column(name = "job_status", nullable = false, length = 20)
    private JobStatus jobStatus;

    // 결제 수단과 상태는 미정
    // private PaymentStatus paymentStatus;
    // private PaymentMeans paymentMeans;

    @Builder
    public ClientOrder(Client client, URL targetUrl, String uuid) {
        this.client = client;
        this.targetUrl = targetUrl;
        this.uuid = UUID.randomUUID().toString();
        this.jobStatus = JobStatus.PENDING;
    }

    // 상태 변경을 위한 의미 있는 비즈니스 메서드 (단순 Setter 지양)
    public void updateStatus(JobStatus newStatus) {
        this.jobStatus = newStatus;
    }

    public enum JobStatus {
        // 대기중, 처리중, 성공, 실패
        PENDING, PROCESSING, SUCCESS, FAILED
    }
}
