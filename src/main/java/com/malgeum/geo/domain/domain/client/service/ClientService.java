package com.malgeum.geo.domain.domain.client.service;

import java.util.Optional;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.malgeum.geo.domain.domain.client.entity.Client;
import com.malgeum.geo.domain.domain.client.entity.Client.OAuthProvider;
import com.malgeum.geo.domain.domain.client.repository.ClientRepository;
import com.malgeum.geo.global.common.DataNotFoundException;

import lombok.RequiredArgsConstructor;

@SuppressWarnings("null")
@Service
@RequiredArgsConstructor
public class ClientService {
    private final ClientRepository clientRepository;
    private final PasswordEncoder passwordEncoder;

    public Client create(String password, String name, String email, String phone, String company) {
        Client client = Client.builder()
                .password(passwordEncoder.encode(password))
                .name(name)
                .email(email)
                .phone(phone)
                .company(company)
                .build();
        clientRepository.save(client);
        return client;
    }

    public Client createOAuthClient(String name, String email,String providerId,OAuthProvider provider) {
        Client client = Client.builder()
                .password("")
                .name(name)
                .email(email)
                .providerId(providerId)
                .provider(provider)
                .phone(null)
                .company(null)
                .build();
        clientRepository.save(client);
        return client;
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

    public Client socialLogin(String name, String email,String providerId,OAuthProvider provider) {
        Optional<Client> _client = this.clientRepository.findByEmail(email);
        if (_client.isPresent()) {
            return _client.get();
        }
        return this.createOAuthClient(name,email,providerId,provider);
    }
}
