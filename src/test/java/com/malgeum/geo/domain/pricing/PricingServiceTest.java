package com.malgeum.geo.domain.pricing;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;

import com.malgeum.geo.domain.domain.analysisjob.entity.AnalysisJob;
import com.malgeum.geo.domain.domain.analysisjob.repository.AnalysisJobRepository;
import com.malgeum.geo.domain.domain.analysisjob.service.AnalysisJobService;
import com.malgeum.geo.domain.domain.client.entity.Client;
import com.malgeum.geo.domain.domain.client.repository.ClientRepository;
import com.malgeum.geo.domain.domain.order.dto.GeoOrderRequest;
import com.malgeum.geo.domain.domain.order.entity.Order;
import com.malgeum.geo.domain.domain.order.repository.OrderRepository;
import com.malgeum.geo.domain.domain.order.repository.PartnerOrderRepository;
import com.malgeum.geo.domain.domain.order.service.OrderService;
import com.malgeum.geo.domain.pricing.account.PricingConfig;
import com.malgeum.geo.domain.pricing.account.entity.PricingAccount;
import com.malgeum.geo.domain.pricing.account.entity.PricingMode;
import com.malgeum.geo.domain.pricing.account.service.PricingService;
import com.malgeum.geo.domain.pricing.academy.entity.Academy;
import com.malgeum.geo.domain.pricing.academy.repository.AcademyRepository;
import com.malgeum.geo.domain.pricing.packorder.entity.PackOrder;
import com.malgeum.geo.domain.pricing.packorder.repository.PackOrderRepository;
import com.malgeum.geo.domain.pricing.partnersubscription.entity.PartnerSubscription;
import com.malgeum.geo.domain.pricing.partnersubscription.repository.PartnerSubscriptionRepository;
import com.malgeum.geo.global.common.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.malgeum.geo.domain.domain.client.dto.ClientProfileResponse;

@DataJpaTest(properties = "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect", showSql = false)
@Import({ PricingService.class, PricingConfig.class, OrderService.class, AnalysisJobService.class })
class PricingServiceTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 5, 12, 0);
    @Autowired PricingService pricing;
    @Autowired OrderService orders;
    @Autowired ClientRepository clients;
    @Autowired OrderRepository orderRepository;
    @Autowired AnalysisJobRepository jobs;
    @Autowired PartnerOrderRepository partnerOrders;
    @Autowired PackOrderRepository packs;
    @Autowired PartnerSubscriptionRepository subscriptions;
    @Autowired AcademyRepository academies;
    @MockitoBean(name = "pricingClock") Clock clock;
    private Client client;
    private PricingAccount account;

    @BeforeEach
    void setup() {
        setNow(NOW);
        client = clients.save(Client.builder().email(UUID.randomUUID() + "@example.com").name("quota-test").build());
        account = pricing.createAccount(client);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(client.getId().toString(), "", List.of()));
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void sixthFreeScanIsRejectedWithoutCreatingOrderOrJob() {
        for (int i = 0; i < 5; i++) scan();
        long orderCount = orderRepository.count();
        long jobCount = jobs.count();
        assertThatThrownBy(this::scan).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getCode()).isEqualTo("FREE_SCAN_LIMIT_EXCEEDED"));
        assertThat(account.getFreeScanUsedCount()).isEqualTo(5);
        assertThat(orderRepository.count()).isEqualTo(orderCount);
        assertThat(jobs.count()).isEqualTo(jobCount);
    }

    @Test
    void packGeneralScansAreUnlimitedAndDoNotSpendCredits() {
        PackOrder pack = pack(PackOrder.PackStatus.PAID, NOW.plusDays(10));
        for (int i = 0; i < 8; i++) {
            Order order = orderRepository.findById(scan()).orElseThrow();
            assertThat(order.getPricingMode()).isEqualTo(PricingMode.PACK);
            assertThat(order.getFreeScanMonthlyBaseDate()).isNull();
        }
        assertThat(pack.getRemainingPages()).isEqualTo(5);
        assertThat(account.getFreeScanUsedCount()).isZero();
        var allowance = pricing.getProfile(client.getId()).scanAllowance();
        assertThat(allowance.unlimited()).isTrue();
        assertThat(allowance.monthlyLimit()).isNull();
        assertThat(allowance.remaining()).isNull();
        JsonNode response = new ObjectMapper().findAndRegisterModules()
                .valueToTree(ClientProfileResponse.from(client, pricing.getProfile(client.getId())));
        assertThat(response.has("plan")).isFalse();
        assertThat(response.at("/pricing/mode").asText()).isEqualTo("PACK");
        assertThat(response.at("/pricing/scanAllowance/unlimited").asBoolean()).isTrue();
    }

    @Test
    void partnerGeneralScansIgnoreAcademyImprovementQuota() {
        Academy academy = academy(partner());
        for (int i = 0; i < 10; i++) academy.usePage();
        for (int i = 0; i < 7; i++) scan();
        assertThat(academy.getMonthlyPagesUsed()).isEqualTo(10);
        assertThat(account.getFreeScanUsedCount()).isZero();
        assertThat(pricing.getProfile(client.getId()).mode()).isEqualTo(PricingMode.PARTNER);
    }

    @Test
    void sameMonthFreeUsageSurvivesPaidScansAndLastCreditConsumption() {
        for (int i = 0; i < 3; i++) scan();
        PackOrder pack = pack(PackOrder.PackStatus.PAID, NOW.plusDays(10));
        for (int i = 0; i < 20; i++) scan();
        for (int i = 0; i < 5; i++) orders.acceptOrder(improvement(PricingMode.PACK));
        assertThat(pack.getStatus()).isEqualTo(PackOrder.PackStatus.USED_UP);
        assertThat(pricing.getProfile(client.getId()).scanAllowance().remaining()).isEqualTo(2);
        scan();
        scan();
        assertThatThrownBy(this::scan).isInstanceOf(ApiException.class);
    }

    @Test
    void expiredOrPendingPackDoesNotGrantUnlimitedScans() {
        pack(PackOrder.PackStatus.PAID, NOW);
        pack(PackOrder.PackStatus.PENDING_PAYMENT, NOW.plusDays(10));
        assertThat(pricing.getProfile(client.getId()).mode()).isEqualTo(PricingMode.FREE);
        scan();
        assertThat(account.getFreeScanUsedCount()).isOne();
    }

    @Test
    void expiredPartnerDoesNotGrantUnlimitedScans() {
        PartnerSubscription sub = partner();
        ReflectionTestUtils.setField(sub, "expiredAt", NOW);
        subscriptions.flush();
        assertThat(pricing.getProfile(client.getId()).mode()).isEqualTo(PricingMode.FREE);
    }

    @Test
    void canceledPartnerIsInactiveEvenBeforeExpirationAndPreservesFreeUsage() {
        for (int i = 0; i < 3; i++) scan();
        PartnerSubscription sub = partner();
        assertThat(pricing.getProfile(client.getId()).mode()).isEqualTo(PricingMode.PARTNER);
        sub.cancel();
        subscriptions.flush();
        assertThat(sub.getStatus()).isEqualTo(PartnerSubscription.SubscriptionStatus.INACTIVE);
        var profile = pricing.getProfile(client.getId());
        assertThat(profile.mode()).isEqualTo(PricingMode.FREE);
        assertThat(profile.availableImprovementSources()).doesNotContain(PricingMode.PARTNER);
        assertThat(profile.scanAllowance().remaining()).isEqualTo(2);
    }

    @Test
    void newKoreanCalendarMonthResetsOnlyFreeUsage() {
        for (int i = 0; i < 5; i++) scan();
        setNow(LocalDateTime.of(2026, 12, 1, 0, 0));
        assertThat(pricing.getProfile(client.getId()).scanAllowance().remaining()).isEqualTo(5);
        assertThat(account.getFreeScanUsedCount()).isEqualTo(5);
        Order order = orderRepository.findById(scan()).orElseThrow();
        assertThat(account.getFreeScanUsedCount()).isOne();
        assertThat(order.getFreeScanMonthlyBaseDate()).isEqualTo(LocalDate.of(2026, 12, 1));
    }

    @Test
    void bothSourcesRequireMemberChoiceBeforeAnyCharge() {
        PackOrder pack = pack(PackOrder.PackStatus.PAID, NOW.plusDays(10));
        Academy academy = academy(partner());
        assertThatThrownBy(() -> orders.acceptOrder(improvement(null)))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo("IMPROVEMENT_SOURCE_REQUIRED"));
        assertThat(pack.getRemainingPages()).isEqualTo(5);
        assertThat(academy.getMonthlyPagesUsed()).isZero();
        assertThat(orderRepository.findAllByClient_IdOrderByCreatedAtDesc(client.getId())).isEmpty();
    }

    @Test
    void selectedPackDoesNotSpendPartnerQuota() {
        PackOrder pack = pack(PackOrder.PackStatus.PAID, NOW.plusDays(10));
        Academy academy = academy(partner());
        orders.acceptOrder(improvement(PricingMode.PACK));
        assertThat(pack.getRemainingPages()).isEqualTo(4);
        assertThat(academy.getMonthlyPagesUsed()).isZero();
        assertThat(partnerOrders.count()).isZero();
    }

    @Test
    void selectedPartnerDoesNotSpendPackCreditsAndCreatesPartnerLink() {
        PackOrder pack = pack(PackOrder.PackStatus.PAID, NOW.plusDays(10));
        Academy academy = academy(partner());
        Long id = orders.acceptOrder(improvement(PricingMode.PARTNER));
        assertThat(pack.getRemainingPages()).isEqualTo(5);
        assertThat(academy.getMonthlyPagesUsed()).isOne();
        assertThat(partnerOrders.findAll()).singleElement()
                .satisfies(link -> assertThat(link.getOrder().getId()).isEqualTo(id));
    }

    @Test
    void exhaustedSelectedPartnerNeverFallsBackToPack() {
        PackOrder pack = pack(PackOrder.PackStatus.PAID, NOW.plusDays(10));
        Academy academy = academy(partner());
        for (int i = 0; i < 10; i++) academy.usePage();
        assertThatThrownBy(() -> orders.acceptOrder(improvement(PricingMode.PARTNER)))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo("ACADEMY_PAGE_LIMIT_EXCEEDED"));
        assertThat(pack.getRemainingPages()).isEqualTo(5);
    }

    @Test
    void unavailableSelectedPackNeverFallsBackToPartner() {
        academy(partner());
        assertThatThrownBy(() -> orders.acceptOrder(improvement(PricingMode.PACK)))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo("INSUFFICIENT_PACK_CREDITS"));
    }

    @Test
    void noPaidEntitlementCannotRequestImprovement() {
        assertThatThrownBy(() -> orders.acceptOrder(improvement(null))).isInstanceOf(ApiException.class);
        assertThat(account.getFreeScanUsedCount()).isZero();
    }

    @Test
    void freeModeCannotBeSelectedForImprovement() {
        assertThatThrownBy(() -> orders.acceptOrder(improvement(PricingMode.FREE)))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo("INVALID_IMPROVEMENT_SOURCE"));
        assertThat(account.getFreeScanUsedCount()).isZero();
    }

    @Test
    void invalidDomainDoesNotConsumeFreeUsage() {
        assertThatThrownBy(() -> orders.acceptOrder(GeoOrderRequest.from("https://example.com", "site", "invalid")))
                .isInstanceOf(com.malgeum.geo.global.common.DataNotFoundException.class);
        assertThat(account.getFreeScanUsedCount()).isZero();
    }

    @Test
    void anotherMembersPackDoesNotGrantAccess() {
        Client other = clients.save(Client.builder().name("other").email(UUID.randomUUID() + "@example.com").build());
        PricingAccount otherAccount = pricing.createAccount(other);
        PackOrder pack = PackOrder.builder().pricingAccount(otherAccount).type(PackOrder.PackType.PACK_5)
                .status(PackOrder.PackStatus.PAID).pricePaid(BigDecimal.ONE).build();
        ReflectionTestUtils.setField(pack, "expiredAt", NOW.plusDays(10));
        packs.saveAndFlush(pack);
        assertThat(pricing.getProfile(client.getId()).mode()).isEqualTo(PricingMode.FREE);
    }

    @Test
    void reevaluationUsesOriginalOrderRightsAfterFreeQuotaIsExhausted() {
        for (int i = 0; i < 5; i++) scan();
        Order baseline = orderRepository.save(orders.createOrder(client, improvement(null)));
        AnalysisJob job = AnalysisJob.create(baseline);
        job.markSucceeded();
        jobs.saveAndFlush(job);
        Long id = orders.acceptReevaluation(baseline.getId());
        Order reeval = orderRepository.findById(id).orElseThrow();
        assertThat(reeval.getParentOrder().getId()).isEqualTo(baseline.getId());
        assertThat(reeval.getFreeScanMonthlyBaseDate()).isNull();
        assertThat(account.getFreeScanUsedCount()).isEqualTo(5);
    }

    @Test
    void createAccountIsIdempotentAndKeepsUsage() {
        scan();
        assertThat(pricing.createAccount(client).getId()).isEqualTo(account.getId());
        assertThat(account.getFreeScanUsedCount()).isOne();
    }

    private Long scan() {
        return orders.acceptOrder(GeoOrderRequest.from("https://example.com/page", "site", "education"));
    }

    private GeoOrderRequest improvement(PricingMode source) {
        return new GeoOrderRequest("https://example.com/page", "site", "education", null,
                List.of(), null, null, null, null, null, true, null, source);
    }

    private PackOrder pack(PackOrder.PackStatus status, LocalDateTime expiresAt) {
        PackOrder pack = PackOrder.builder().pricingAccount(account).type(PackOrder.PackType.PACK_5)
                .status(status).pricePaid(BigDecimal.ONE).build();
        ReflectionTestUtils.setField(pack, "expiredAt", expiresAt);
        return packs.saveAndFlush(pack);
    }

    private PartnerSubscription partner() {
        PartnerSubscription sub = PartnerSubscription.builder().pricingAccount(account)
                .tier(PartnerSubscription.PartnerTier.PARTNER).status(PartnerSubscription.SubscriptionStatus.ACTIVE)
                .monthlyPrice(BigDecimal.ONE).build();
        ReflectionTestUtils.setField(sub, "expiredAt", NOW.plusDays(20));
        return subscriptions.saveAndFlush(sub);
    }

    private Academy academy(PartnerSubscription sub) {
        return academies.saveAndFlush(Academy.builder().partnerSubscription(sub).domain("example.com")
                .name("test-academy").sizeType(Academy.SizeType.SMALL).build());
    }

    private void setNow(LocalDateTime time) {
        when(clock.withZone(PricingConfig.BILLING_ZONE))
                .thenReturn(Clock.fixed(time.atZone(PricingConfig.BILLING_ZONE).toInstant(), PricingConfig.BILLING_ZONE));
    }
}
