package com.malgeum.geo.serivce;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.password.NoOpPasswordEncoder;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;

import com.malgeum.geo.SignUpForm;
import com.malgeum.geo.domain.domain.Client;
import com.malgeum.geo.global.common.ClientRepository;
import com.malgeum.geo.service.AuthService;
import org.springframework.boot.test.context.TestConfiguration;

@DataJpaTest
@Import({ AuthService.class, AuthServiceTest.TestConfig.class })
public class AuthServiceTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private ClientRepository clientRepository;

    @Test
    @DisplayName("로그인 폼을 작성하고, 정상적으로 해당 계정이 생성되고 저장까지 완료돼야한다.")
    void signUpFormTest() {
        // given
        String email = "test-"+UUID.randomUUID().toString().substring(0, 8)+"@example.com";
        
        SignUpForm form = new SignUpForm();
        form.setName("테스트 사용자");
        form.setCompany("malgeum");
        form.setPhone("01012345678");
        form.setPassword1("password123!");
        form.setPassword2("password123!");
        form.setEmail(email);

        BindingResult bindingResult = new BeanPropertyBindingResult(form, "signUpForm");

        // when
        authService.signUp(form, bindingResult);

        // then
        Optional<Client> savedClient = clientRepository.findByEmail(email);
        assertThat(savedClient).isPresent();
        assertThat(savedClient.get().getName()).isEqualTo(form.getName());
        assertThat(savedClient.get().getEmail()).isEqualTo(form.getEmail());
        assertThat(savedClient.get().getCompany()).isEqualTo(form.getCompany());
        assertThat(savedClient.get().getPhone()).isEqualTo(form.getPhone());

        System.out.println(savedClient.get().getName());
        System.out.println(savedClient.get().getEmail());
        System.out.println(savedClient.get().getPassword());
    }

    @TestConfiguration
    static class TestConfig {
        @SuppressWarnings("deprecation")
        @Bean
        PasswordEncoder passwordEncoder() {
            return NoOpPasswordEncoder.getInstance();
        }
    }
}
