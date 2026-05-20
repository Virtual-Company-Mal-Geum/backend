package com.malgeum.geo.service;

import java.util.Optional;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.validation.BindingResult;

import com.malgeum.geo.SignUpForm;
import com.malgeum.geo.domain.domain.Client;
import com.malgeum.geo.global.common.ClientRepository;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final ClientRepository userRepository;

    @Transactional
    public void signUp(SignUpForm form, BindingResult bindingResult) {
        String name = form.getName();
        String email = form.getEmail();
        String phone = form.getPhone();
        String company = form.getCompany();
        String password1 = form.getPassword1();
        String password2 = form.getPassword2();

        // 회원 중복 확인
        Optional<Client> checkUsername = userRepository.findByEmail(email);
        if (checkUsername.isPresent()) {
            throw new IllegalArgumentException("중복된 사용자가 존재합니다.");
        }

        // 확인 비밀번호와 동일한지 확인
        if (!password1.equals(password2)) {
            bindingResult.rejectValue("password2", "passwordInCorrect", "2개의 패스워드가 일치하지 않습니다.");
            throw new IllegalArgumentException("2개의 패스워드가 일치하지 않습니다.");
        }

        // 사용자 등록
        Client client = Client.builder()
                .password(new BCryptPasswordEncoder().encode(password2))
                .name(name)
                .email(email)
                .phone(phone)
                .company(company)
                .build();

        userRepository.save(client);
    }

}