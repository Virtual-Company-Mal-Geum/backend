package com.malgeum.geo.service;

import org.springframework.stereotype.Service;

import com.malgeum.geo.domain.domain.Client;
import com.malgeum.geo.global.common.ClientRepository;

import lombok.RequiredArgsConstructor;

@SuppressWarnings("null")
@Service
@RequiredArgsConstructor
public class ClientService {
    private final ClientRepository clientRepository;

    public void create(String name, String email, String phone, String company, String targetUrl){
        Client client = Client.builder()
                .name(name)
                .email(email)
                .phone(phone)
                .company(company)
                .build();
        clientRepository.save(client);
    }
    
}
