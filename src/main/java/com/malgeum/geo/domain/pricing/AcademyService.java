package com.malgeum.geo.domain.pricing;

import java.time.LocalDateTime;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.malgeum.geo.domain.pricing.academy.dto.AcademyRegisterRequest;
import com.malgeum.geo.domain.pricing.academy.entity.Academy;
import com.malgeum.geo.domain.pricing.academy.repository.AcademyRepository;
import com.malgeum.geo.domain.pricing.partnersubscription.entity.PartnerSubscription;
import com.malgeum.geo.domain.pricing.partnersubscription.repository.PartnerSubscriptionRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AcademyService {
    private final AcademyRepository academyRepository;
    private final PartnerSubscriptionRepository partnerSubscriptionRepository;

    @Transactional
    public Long registerAcademy(AcademyRegisterRequest request) {
        Long clientId = Long.valueOf(SecurityContextHolder.getContext().getAuthentication().getName());

        PartnerSubscription subscription = partnerSubscriptionRepository
                .findByClientIdAndStatusAndExpiredAtAfter(
                        clientId, PartnerSubscription.SubscriptionStatus.ACTIVE, LocalDateTime.now())
                .orElseThrow(() -> new IllegalStateException("활성화된 Partner 구독이 없습니다."));

        long activeCount = academyRepository.countByPartnerSubscription_IdAndStatus(
                subscription.getId(), Academy.AcademyStatus.ACTIVE);
        if (activeCount >= subscription.getAcademiesCapacity()) {
            throw new IllegalStateException("등록 가능한 학원 수(%d곳)를 초과했습니다.".formatted(subscription.getAcademiesCapacity()));
        }

        String domain = Academy.normalizeDomain(request.domain());
        if (academyRepository.existsByPartnerSubscription_IdAndDomainAndStatus(
                subscription.getId(), domain, Academy.AcademyStatus.ACTIVE)) {
            throw new IllegalStateException("이미 등록된 학원 도메인입니다: " + domain);
        }

        Academy academy = Academy.builder()
                .partnerSubscription(subscription)
                .name(request.name())
                .domain(domain)
                .sizeType(request.sizeType())
                .build();
        return academyRepository.save(academy).getId();
    }
}
