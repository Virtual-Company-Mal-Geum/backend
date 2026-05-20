package com.malgeum.geo.global.common;

import com.malgeum.geo.domain.domain.Client;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.annotation.Rollback;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ClientRepositoryTest {

    @Autowired
    private ClientRepository clientRepository;

    @Test
    @Rollback(false)
    @DisplayName("Client 엔티티가 DB에 정상 저장된다")
    void saveClient() {
        // given
        Client client = Client.builder()
                .password("test-password")
                .name("테스트유저")
                .company("말금")
                .email("test001@malgeum.com")
                .phone("010-1234-5678")
                .build();

        // when
        Client savedClient = clientRepository.save(client);

        // then
        assertThat(savedClient.getId()).isNotNull();
        assertThat(savedClient.getName()).isEqualTo("테스트유저");
        assertThat(savedClient.getEmail()).isEqualTo("test001@malgeum.com");
        assertThat(savedClient.getPhone()).isEqualTo("010-1234-5678");
        assertThat(savedClient.getStatus()).isEqualTo(Client.ClientStatus.ACTIVE);
    }
}