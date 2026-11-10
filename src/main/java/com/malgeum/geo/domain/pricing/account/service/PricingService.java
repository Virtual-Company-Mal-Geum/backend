package com.malgeum.geo.domain.pricing.account.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.malgeum.geo.domain.domain.client.entity.Client;
import com.malgeum.geo.domain.domain.order.entity.Order;
import com.malgeum.geo.domain.pricing.academy.entity.Academy;
import com.malgeum.geo.domain.pricing.academy.repository.AcademyRepository;
import com.malgeum.geo.domain.pricing.account.PricingConfig;
import com.malgeum.geo.domain.pricing.account.dto.PricingProfileResponse;
import com.malgeum.geo.domain.pricing.account.dto.PricingProfileResponse.ScanAllowance;
import com.malgeum.geo.domain.pricing.account.entity.PricingAccount;
import com.malgeum.geo.domain.pricing.account.entity.PricingMode;
import com.malgeum.geo.domain.pricing.account.repository.PricingAccountRepository;
import com.malgeum.geo.domain.pricing.packorder.entity.PackOrder;
import com.malgeum.geo.domain.pricing.packorder.entity.PackOrder.PackStatus;
import com.malgeum.geo.domain.pricing.packorder.repository.PackOrderRepository;
import com.malgeum.geo.domain.pricing.partnersubscription.entity.PartnerSubscription;
import com.malgeum.geo.domain.pricing.partnersubscription.entity.PartnerSubscription.SubscriptionStatus;
import com.malgeum.geo.domain.pricing.partnersubscription.repository.PartnerSubscriptionRepository;
import com.malgeum.geo.global.common.ApiException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PricingService {
    private final PricingAccountRepository accountRepository;
    private final PackOrderRepository packRepository;
    private final PartnerSubscriptionRepository subscriptionRepository;
    private final AcademyRepository academyRepository;
    private final Clock pricingClock;

    @Transactional
    public PricingAccount createAccount(Client client) {
        return accountRepository.findByClient_Id(client.getId())
                .orElseGet(() -> accountRepository.save(new PricingAccount(client, now().toLocalDate())));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public PricingAccount lockAccount(Long clientId) {
        return accountRepository.findByClientIdForUpdate(clientId).orElseThrow(PricingService::missingAccount);
    }

    @Transactional(readOnly = true)
    public PricingProfileResponse getProfile(Long clientId) {
        PricingAccount account = accountRepository.findByClient_Id(clientId)
                .orElseThrow(PricingService::missingAccount);
        LocalDateTime now = now();
        PartnerSubscription partner = getActivePartner(account.getId(), now);
        PackOrder pack = getUsablePack(account.getId(), now);
        PricingMode mode = setAvaliablePricingMode(partner, pack);
        boolean unlimited = mode != PricingMode.FREE;
        int used = account.freeScansUsedIn(now.toLocalDate());
        List<PricingMode> sources = new ArrayList<>();
        if (pack != null) sources.add(PricingMode.PACK);
        if (partner != null) sources.add(PricingMode.PARTNER);
        return new PricingProfileResponse(mode, partner == null ? null : partner.getTier(), List.copyOf(sources),
                new ScanAllowance(unlimited, YearMonth.from(now), unlimited ? null : PricingAccount.FREE_MONTHLY_SCAN_LIMIT,
                        used, unlimited ? null : PricingAccount.FREE_MONTHLY_SCAN_LIMIT - used));
    }

    // 주문·작업 저장과 같은 트랜잭션에서 잠금과 차감을 처리해야 실패 시 함께 롤백된다.
    @Transactional(propagation = Propagation.MANDATORY)
    public Academy authorizeOrder(Order order, PricingMode requestedSource) {
        PricingAccount account = lockAccount(order.getClient().getId());
        LocalDateTime now = now();
        PartnerSubscription partner = getActivePartner(account.getId(), now);
        PackOrder pack = getUsablePack(account.getId(), now);
        if (!order.isWithImprovement()) {
            PricingMode mode = setAvaliablePricingMode(partner, pack);
            account.acceptGeneralScan(mode, now.toLocalDate());
            order.recordPricing(mode, mode == PricingMode.FREE ? now.toLocalDate().withDayOfMonth(1) : null);
            return null;
        }

        PricingMode source = determinePricingModeForImprovement(requestedSource, partner, pack);
        order.recordPricing(source, null);
        if (source == PricingMode.PACK) {
            if (pack == null) {
                throw new ApiException(HttpStatus.CONFLICT, "INSUFFICIENT_PACK_CREDITS", "사용 가능한 Pack 크레딧이 없습니다.");
            }
            // shortcut: 기존 모델처럼 접수 시 소진한다. 예약·최종 실패 반환은 크레딧 원장 구현 시 연결한다.
            pack.usePage(now);
            return null;
        }
        if (partner == null) {
            throw new ApiException(HttpStatus.FORBIDDEN, "PARTNER_SUBSCRIPTION_REQUIRED", "유효한 Partner 구독이 없습니다.");
        }
        String domain = Academy.normalizeDomain(order.getTargetUrl());
        Academy academy = academyRepository
                .findByPartnerSubscription_IdAndDomainAndStatus(partner.getId(), domain, Academy.AcademyStatus.ACTIVE)
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "ACADEMY_NOT_REGISTERED", "등록되지 않은 학원 도메인입니다."));
        if (academy.getMonthlyPagesUsed() >= Academy.MONTHLY_PAGE_QUOTA) {
            throw new ApiException(HttpStatus.CONFLICT, "ACADEMY_PAGE_LIMIT_EXCEEDED", "해당 학원의 이번 달 개선안 한도를 모두 사용했습니다.");
        }
        academy.usePage();
        return academy;
    }

    public LocalDateTime now() {
        return LocalDateTime.now(pricingClock.withZone(PricingConfig.BILLING_ZONE));
    }

    private PartnerSubscription getActivePartner(Long accountId, LocalDateTime now) {
        return subscriptionRepository
                .findByPricingAccount_IdAndStatusAndExpiredAtAfter(
                        accountId, SubscriptionStatus.ACTIVE, now).orElse(null);
    }

    private PackOrder getUsablePack(Long accountId, LocalDateTime now) {
        return packRepository
                .findFirstByPricingAccount_IdAndStatusAndRemainingPagesGreaterThanAndExpiredAtAfterOrderByExpiredAtAscIdAsc(
                        accountId, PackStatus.PAID, 0, now).orElse(null);
    }

    private static PricingMode setAvaliablePricingMode(PartnerSubscription partner, PackOrder pack) {
        return partner != null ? PricingMode.PARTNER : pack != null ? PricingMode.PACK : PricingMode.FREE;
    }

    private static PricingMode determinePricingModeForImprovement(PricingMode requested, PartnerSubscription partner, PackOrder pack) {
        if (requested == PricingMode.FREE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_IMPROVEMENT_SOURCE", "개선안 이용권은 PACK 또는 PARTNER를 선택해 주세요.");
        }
        if (requested != null) return requested;
        if (partner != null && pack != null) {
            throw new ApiException(HttpStatus.CONFLICT, "IMPROVEMENT_SOURCE_REQUIRED", "개선안에 사용할 Pack 또는 Partner 이용권을 선택해 주세요.");
        }
        if (partner != null) return PricingMode.PARTNER;
        if (pack != null) return PricingMode.PACK;
        throw new ApiException(HttpStatus.CONFLICT, "INSUFFICIENT_PACK_CREDITS", "개선안에 사용할 유효한 이용권이 없습니다.");
    }

    private static ApiException missingAccount() {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "PRICING_ACCOUNT_NOT_INITIALIZED",
                "이용권 정보를 확인할 수 없습니다. 잠시 후 다시 시도해 주세요.");
    }
}
