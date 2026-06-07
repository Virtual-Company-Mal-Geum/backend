package com.malgeum.geo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.web.server.ResponseStatusException;

import com.malgeum.geo.LoginRequest;
import com.malgeum.geo.SignUpForm;
import com.malgeum.geo.domain.domain.client.entity.Client;
import com.malgeum.geo.domain.domain.client.repository.ClientRepository;
import com.malgeum.geo.global.auth.JwtTokenProvider;
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
        String token = authService.signup(form, bindingResult);

        // then
        Optional<Client> savedClient = clientRepository.findByEmail(email);
        assertThat(savedClient).isPresent();
        assertThat(token).isNotBlank();
        assertThat(savedClient.get().getName()).isEqualTo(form.getName());
        assertThat(savedClient.get().getEmail()).isEqualTo(form.getEmail());
        assertThat(savedClient.get().getCompany()).isEqualTo(form.getCompany());
        assertThat(savedClient.get().getPhone()).isEqualTo(form.getPhone());

        System.out.println(savedClient.get().getName());
        System.out.println(savedClient.get().getEmail());
        System.out.println(savedClient.get().getPassword());
    }

    @Test
    @DisplayName("존재하지 않는 이메일로 로그인하면 인증에 실패해야 한다.")
    void loginWithUnknownEmail_ShouldFail() {
        LoginRequest request = new LoginRequest();
        request.setEmail("missing-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com");
        request.setPassword("password123!");

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("401 UNAUTHORIZED");
    }

    @Test
    @DisplayName("비밀번호가 일치하지 않으면 인증에 실패해야 한다.")
    void loginWithWrongPassword_ShouldFail() {
        String email = "test-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        SignUpForm form = new SignUpForm();
        form.setName("테스트 사용자");
        form.setCompany("malgeum");
        form.setPhone("01012345678");
        form.setPassword1("password123!");
        form.setPassword2("password123!");
        form.setEmail(email);
        authService.signup(form, new BeanPropertyBindingResult(form, "signUpForm"));

        LoginRequest request = new LoginRequest();
        request.setEmail(email);
        request.setPassword("wrong-password");

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("401 UNAUTHORIZED");
    }

    @Test
    @DisplayName("회원가입한 이메일과 비밀번호로 로그인하면 JWT 토큰이 발급돼야 한다.")
    void loginAfterSignup_ShouldReturnToken() {
        String email = "test-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        SignUpForm form = new SignUpForm();
        form.setName("테스트 사용자");
        form.setCompany("malgeum");
        form.setPhone("01012345678");
        form.setPassword1("password123!");
        form.setPassword2("password123!");
        form.setEmail(email);
        authService.signup(form, new BeanPropertyBindingResult(form, "signUpForm"));

        LoginRequest request = new LoginRequest();
        request.setEmail(email);
        request.setPassword("password123!");

        String token = authService.login(request);

        assertThat(token).isNotBlank();
    }

    @Test
    @DisplayName("레거시 평문 비밀번호 계정은 로그인 성공 후 암호화된 비밀번호로 마이그레이션돼야 한다.")
    void loginWithLegacyPlainPassword_ShouldMigratePassword() {
        String email = "legacy-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        Client client = Client.builder()
                .name("레거시 사용자")
                .company("malgeum")
                .phone("01012345678")
                .email(email)
                .password("password123!")
                .build();
        clientRepository.save(client);

        LoginRequest request = new LoginRequest();
        request.setEmail(email);
        request.setPassword("password123!");

        String token = authService.login(request);

        Client migrated = clientRepository.findByEmail(email).orElseThrow();
        assertThat(token).isNotBlank();
        assertThat(migrated.getPassword()).isNotEqualTo("password123!");
        assertThat(migrated.getPassword()).startsWith("{bcrypt}");
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        PasswordEncoder passwordEncoder() {
            return PasswordEncoderFactories.createDelegatingPasswordEncoder();
        }

        @Bean
        JwtTokenProvider jwtTokenProvider() {
            return new JwtTokenProvider(
                    "testtesttesttesttesttesttesttesttesttesttesttesttesttesttesttest");
        }
    }
}
