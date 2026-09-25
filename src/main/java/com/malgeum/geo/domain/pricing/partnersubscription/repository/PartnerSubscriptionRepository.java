package com.malgeum.geo.domain.pricing.partnersubscription.repository;

import com.malgeum.geo.domain.pricing.partnersubscription.entity.PartnerSubscription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface PartnerSubscriptionRepository extends JpaRepository<PartnerSubscription, Long> {
    Optional<PartnerSubscription> findByClientIdAndStatusAndExpiredAtAfter(
            Long clientId, PartnerSubscription.SubscriptionStatus status, LocalDateTime now);
}
