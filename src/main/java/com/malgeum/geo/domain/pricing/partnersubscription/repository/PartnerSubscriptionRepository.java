package com.malgeum.geo.domain.pricing.partnersubscription.repository;

import com.malgeum.geo.domain.pricing.partnersubscription.entity.PartnerSubscription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface PartnerSubscriptionRepository extends JpaRepository<PartnerSubscription, Long> {
    //사용자가 결제해놓은 PartnerSubscription 중에서 유효한 것들 가져오기. 그 중에서도 가장 최근꺼부터 선택.
    Optional<PartnerSubscription> findByPricingAccount_IdAndStatusAndExpiredAtAfter(
            Long accountId, PartnerSubscription.SubscriptionStatus status, LocalDateTime now);
}
