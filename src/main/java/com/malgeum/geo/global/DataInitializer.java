package com.malgeum.geo.global;

import java.util.Objects;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import com.malgeum.geo.domain.domain.Client;
import com.malgeum.geo.global.common.ClientRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {
    // 테스트용 고객 데이터 초기화 클래스
    private final ClientRepository clientRepository;

    @Override
    public void run(String... args) throws Exception {
        if (clientRepository.findById(1L).isEmpty()) {
            Client testClient = Client.builder()
                    .company("상상기업 테스트")
                    .name("파트너님")
                    .phone("010-1234-5678")
                    .email("test@example.com")
                    .build();

            // 주의: IDENTITY 전략이면 ID를 강제로 지정하기 어려울 수 있으니
            // 실제 생성된 ID를 로그로 찍어서 확인하거나,
            // 가짜 로그인 API에서 이 ID를 사용하도록 맞추면 됩니다.
            Client saved = clientRepository.save(Objects.requireNonNull(testClient));
            System.out.println("✅ 테스트용 고객 생성 완료! ID: " + saved.getId());
        }
    }
}
