package com.malgeum.geo.domain.pricing.account.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.malgeum.geo.domain.pricing.account.entity.PricingAccount;

import jakarta.persistence.LockModeType;

public interface PricingAccountRepository extends JpaRepository<PricingAccount, Long> {
    Optional<PricingAccount> findByClient_Id(Long clientId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from PricingAccount a where a.client.id = :clientId")
    Optional<PricingAccount> findByClientIdForUpdate(@Param("clientId") Long clientId);
}
