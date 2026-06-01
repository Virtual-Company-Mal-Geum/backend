package com.malgeum.geo.service;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.validation.BindingResult;
import org.springframework.web.server.ResponseStatusException;

import com.malgeum.geo.LoginRequest;
import com.malgeum.geo.SignUpForm;
import com.malgeum.geo.domain.domain.client.entity.Client;
import com.malgeum.geo.domain.domain.client.repository.ClientRepository;
import com.malgeum.geo.global.auth.JwtTokenProvider;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final ClientRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    @Transactional
    public void signup(SignUpForm form, BindingResult bindingResult) {
        String name = form.getName();
        String email = form.getEmail();
        String phone = form.getPhone();
        String company = form.getCompany();
        String password1 = form.getPassword1();
        String password2 = form.getPassword2();

        if (userRepository.findByEmail(email).isPresent()) {
            throw new IllegalArgumentException("이미 사용 중인 이메일입니다.");
        }

        if (!password1.equals(password2)) {
            bindingResult.rejectValue("password2", "passwordInCorrect", "비밀번호 확인이 일치하지 않습니다.");
            throw new IllegalArgumentException("비밀번호 확인이 일치하지 않습니다.");
        }

        Client client = Client.builder()
                .password(passwordEncoder.encode(password2))
                .name(name)
                .email(email)
                .phone(phone)
                .company(company)
                .build();

        userRepository.save(client);
    }

    @Transactional
    public String login(LoginRequest request) {
        Client client = userRepository.findByEmail(request.getEmail())
                .orElseThrow(
                        () -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."));

        if (client.getPassword() == null || !passwordEncoder.matches(request.getPassword(), client.getPassword())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다.");
        }

        return jwtTokenProvider.generateToken(client.getId().toString(), List.of("ROLE_USER"));
    }
}