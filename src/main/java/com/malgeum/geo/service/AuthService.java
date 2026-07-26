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
import com.malgeum.geo.dto.PasswordUpdateRequest;
import com.malgeum.geo.global.auth.JwtTokenProvider;
import com.malgeum.geo.global.common.DataNotFoundException;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final ClientRepository clientRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    @Transactional
    public String signup(SignUpForm form, BindingResult bindingResult) {
        String name = form.getName();
        String email = form.getEmail();
        String phone = form.getPhone();
        String company = form.getCompany();
        String password1 = form.getPassword1();
        String password2 = form.getPassword2();

        if (clientRepository.findByEmail(email).isPresent()) {
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

        Client savedClient = clientRepository.save(client);
        return jwtTokenProvider.generateToken(savedClient.getId().toString(), List.of("ROLE_USER"));
    }

    @Transactional
    public String login(LoginRequest request) {
        Client client = clientRepository.findByEmail(request.getEmail())
                .orElseThrow(
                        () -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."));

        if (!matchesPassword(request.getPassword(), client)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다.");
        }

        return jwtTokenProvider.generateToken(client.getId().toString(), List.of("ROLE_USER"));
    }

    public boolean matchesPassword(String rawPassword, Client client) {
        String storedPassword = client.getPassword();
        if (storedPassword == null || storedPassword.isBlank()) {
            return false;
        }

        try {
            return passwordEncoder.matches(rawPassword, storedPassword);
        } catch (IllegalArgumentException e) {
            if (!storedPassword.equals(rawPassword)) {
                return false;
            }

            client.updatePassword(passwordEncoder.encode(rawPassword));
            clientRepository.save(client);
            return true;
        }
    }

    @Transactional
    public void changePassword(Long clientId, PasswordUpdateRequest request) {
        Client client = clientRepository.findById(clientId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "인증 정보가 올바르지 않습니다."));
        if(!matchesPassword(request.originPassword(), client)){
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "기존 비밀번호와 일치하지 않습니다.");
        }        
        if (matchesPassword(request.newPassword(), client)){
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "기존과 다른 비밀번호를 사용해 주세요.");
        }
        client.updatePassword(passwordEncoder.encode(request.newPassword()));
    }
}