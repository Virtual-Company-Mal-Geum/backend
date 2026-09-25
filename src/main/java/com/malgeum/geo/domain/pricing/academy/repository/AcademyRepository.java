package com.malgeum.geo.domain.pricing.academy.repository;

import com.malgeum.geo.domain.pricing.academy.entity.Academy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AcademyRepository extends JpaRepository<Academy, Long> {
    long countByPartnerSubscription_IdAndStatus(Long partnerSubscriptionId, Academy.AcademyStatus status);

    boolean existsByPartnerSubscription_IdAndDomainAndStatus(
            Long partnerSubscriptionId, String domain, Academy.AcademyStatus status);

    Optional<Academy> findByPartnerSubscription_Client_IdAndDomainAndStatus(
            Long clientId, String domain, Academy.AcademyStatus status);
}
