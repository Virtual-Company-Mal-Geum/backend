package com.malgeum.geo.global.common;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.malgeum.geo.domain.domain.Client;

public interface ClientRepository extends JpaRepository<Client, Long> {
    List<Client> findByName(String name);

    Optional<Client> findByEmail(String email);
}
