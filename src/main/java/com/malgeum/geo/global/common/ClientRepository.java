package com.malgeum.geo.global.common;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.malgeum.geo.domain.domain.Client;
import com.malgeum.geo.domain.domain.Client.OAuthProvider;

public interface ClientRepository extends JpaRepository<Client, Long> {
    List<Client> findByName(String name);

    Optional<Client> findByEmail(String email);

    Optional<Client> findByProviderAndProviderId(OAuthProvider provider, String providerId);

    boolean existsByEmail(String email);
}
