package com.malgeum.geo.service;

import java.util.Optional;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.malgeum.geo.domain.domain.Client;
import com.malgeum.geo.global.common.ClientRepository;
import com.malgeum.geo.global.common.DataNotFoundException;

import lombok.RequiredArgsConstructor;

@SuppressWarnings("null")
@Service
@RequiredArgsConstructor
public class ClientService {
    private final ClientRepository clientRepository;
    private final PasswordEncoder passwordEncoder;

    public Client create(String registerId, String password, String name, String email, String phone, String company) {
        Client client = Client.builder()
                .password(new BCryptPasswordEncoder().encode(password))
                .name(name)
                .email(email)
                .phone(phone)
                .company(company)
                .build();
        clientRepository.save(client);
        return client;
    }

    public Client create(String registerId, String password, String name, String email) {
        Client client = Client.builder()
                .password(new BCryptPasswordEncoder().encode(password))
                .name(name)
                .email(email)
                .build();
        clientRepository.save(client);
        return client;
    }

    public Client getClientByRegisterId(String registerId) {
        Optional<Client> _client = this.clientRepository.findByRegisterId(registerId);
        if (_client.isPresent()) {
            return _client.get();
        }
        throw new DataNotFoundException("registerId not found");
    }

    public Client getClientByEmail(String email) {
        Optional<Client> _client = this.clientRepository.findByEmail(email);
        if (_client.isPresent()) {
            return _client.get();
        }
        throw new DataNotFoundException("email not found");
    }

    public Client updatePassword(Client client, String newPassword) {
        client.updatePassword(passwordEncoder.encode(newPassword));
        clientRepository.save(client);
        return client;
    }

    public boolean MatchPassword(String rawPassword, String encodedPassword) {
        return passwordEncoder.matches(rawPassword, encodedPassword);
    }

    public Client socialLogin(String registerId, String name, String email) {
        Optional<Client> _client = this.clientRepository.findByEmail(email);
        if (_client.isPresent()) {
            return _client.get();
        }
        return this.create(registerId, "", name, email);
    }
}
