package com.malgeum.geo.domain.pricing;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.malgeum.geo.domain.domain.analysisjob.repository.AnalysisJobRepository;
import com.malgeum.geo.domain.domain.analysisjob.service.AnalysisJobService;
import com.malgeum.geo.domain.domain.client.entity.Client;
import com.malgeum.geo.domain.domain.client.repository.ClientRepository;
import com.malgeum.geo.domain.domain.order.dto.GeoOrderRequest;
import com.malgeum.geo.domain.domain.order.repository.OrderRepository;
import com.malgeum.geo.domain.domain.order.service.OrderService;
import com.malgeum.geo.domain.pricing.account.PricingConfig;
import com.malgeum.geo.domain.pricing.account.repository.PricingAccountRepository;
import com.malgeum.geo.domain.pricing.account.service.PricingService;
import com.malgeum.geo.global.common.ApiException;

@EnabledIfEnvironmentVariable(named = "PRICING_TEST_DB_URL", matches = "jdbc:postgresql:.*")
@DataJpaTest(properties = {
        "spring.datasource.url=${PRICING_TEST_DB_URL}", "spring.datasource.username=pricing_test",
        "spring.datasource.password=", "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop"
}, showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ PricingConfig.class, PricingService.class, OrderService.class, AnalysisJobService.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PricingPostgresIntegrationTest {
    @Autowired ClientRepository clients;
    @Autowired PricingAccountRepository accounts;
    @Autowired OrderRepository orders;
    @Autowired AnalysisJobRepository jobs;
    @Autowired PricingService pricing;
    @Autowired OrderService orderService;
    @Autowired DataSource dataSource;
    @MockitoSpyBean AnalysisJobService queue;
    private Long clientId;

    @BeforeEach
    void createMember() {
        Client client = clients.save(Client.builder().name("postgres-test").email(UUID.randomUUID() + "@example.com").build());
        pricing.createAccount(client);
        clientId = client.getId();
        authenticate();
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void concurrentSixthRequestIsPreventedByPostgresRowLock() throws Exception {
        for (int i = 0; i < 4; i++) submit();
        ExecutorService pool = Executors.newFixedThreadPool(3);
        CountDownLatch ready = new CountDownLatch(3);
        CountDownLatch go = new CountDownLatch(1);
        try {
            List<Future<String>> requests = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                requests.add(pool.submit(() -> {
                    authenticate();
                    ready.countDown();
                    try {
                        if (!go.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("request barrier timeout");
                        submit();
                        return "ACCEPTED";
                    } catch (ApiException e) {
                        return e.getCode();
                    } finally {
                        SecurityContextHolder.clearContext();
                    }
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            List<String> results = new ArrayList<>();
            for (Future<String> request : requests) results.add(request.get(20, TimeUnit.SECONDS));
            assertThat(results).containsExactlyInAnyOrder("ACCEPTED", "FREE_SCAN_LIMIT_EXCEEDED", "FREE_SCAN_LIMIT_EXCEEDED");
            assertThat(accounts.findByClient_Id(clientId).orElseThrow().getFreeScanUsedCount()).isEqualTo(5);
            var accepted = orders.findAllByClient_IdOrderByCreatedAtDesc(clientId);
            assertThat(accepted).hasSize(5);
            assertThat(jobs.findAllByOrder_IdIn(accepted.stream().map(o -> o.getId()).toList())).hasSize(5);
        } finally {
            go.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void queueSaveFailureRollsBackQuotaAndOrderInRealTransaction() {
        doThrow(new IllegalStateException("queue storage unavailable")).when(queue).enqueue(any());
        assertThatThrownBy(this::submit).hasMessageContaining("queue storage unavailable");
        assertThat(accounts.findByClient_Id(clientId).orElseThrow().getFreeScanUsedCount()).isZero();
        assertThat(orders.findAllByClient_IdOrderByCreatedAtDesc(clientId)).isEmpty();
    }

    @Test
    void legacyMigrationPreservesOwnershipBalancesAndCanBeRepeated() throws Exception {
        String schema = "pricing_migration_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection connection = dataSource.getConnection(); Statement sql = connection.createStatement()) {
            sql.execute("CREATE SCHEMA " + schema);
            connection.setSchema(schema);
            try {
                sql.execute("CREATE TABLE client(id BIGINT PRIMARY KEY, plan VARCHAR(20), free_promotion_page_count INT)");
                sql.execute("CREATE TABLE pack_order(id BIGINT PRIMARY KEY, client_id BIGINT NOT NULL REFERENCES client(id), remaining_pages INT, price_paid NUMERIC)");
                sql.execute("CREATE TABLE partner_subscription(id BIGINT PRIMARY KEY, client_id BIGINT NOT NULL REFERENCES client(id), tier VARCHAR(30), expired_at TIMESTAMP)");
                sql.execute("CREATE TABLE client_order(id BIGINT PRIMARY KEY)");
                sql.execute("INSERT INTO client VALUES (11,'FREE',5),(22,'PARTNER',5)");
                sql.execute("INSERT INTO pack_order VALUES (33,11,4,49000)");
                sql.execute("INSERT INTO partner_subscription VALUES (44,22,'PARTNER','2026-12-01')");
                String migration = new ClassPathResource("db/migration/V20261105__pricing_account.sql")
                        .getContentAsString(StandardCharsets.UTF_8);
                sql.execute(migration);
                sql.execute(migration);
                try (var rows = sql.executeQuery("SELECT a.client_id, p.remaining_pages, p.price_paid FROM pack_order p JOIN pricing_account a ON a.id=p.pricing_account_id")) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getLong(1)).isEqualTo(11);
                    assertThat(rows.getInt(2)).isEqualTo(4);
                    assertThat(rows.getBigDecimal(3)).isEqualByComparingTo("49000");
                }
                try (var rows = sql.executeQuery("SELECT a.client_id, p.tier, p.expired_at FROM partner_subscription p JOIN pricing_account a ON a.id=p.pricing_account_id")) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getLong(1)).isEqualTo(22);
                    assertThat(rows.getString(2)).isEqualTo("PARTNER");
                    assertThat(rows.getTimestamp(3).toLocalDateTime().toString()).startsWith("2026-12-01");
                }
                try (var rows = sql.executeQuery("SELECT count(*), sum(free_scan_used_count) FROM pricing_account")) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getLong(1)).isEqualTo(2);
                    assertThat(rows.getLong(2)).isZero();
                }
            } finally {
                sql.execute("ROLLBACK");
                connection.setSchema("public");
                sql.execute("DROP SCHEMA " + schema + " CASCADE");
            }
        }
    }

    @Test
    void subscriptionStatusMigrationReplacesLegacyCheckAndPreservesActiveSubscriptions() throws Exception {
        String schema = "pricing_status_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection connection = dataSource.getConnection(); Statement sql = connection.createStatement()) {
            sql.execute("CREATE SCHEMA " + schema);
            connection.setSchema(schema);
            try {
                sql.execute("CREATE TABLE partner_subscription(id BIGINT PRIMARY KEY, status VARCHAR(20) NOT NULL, "
                        + "CONSTRAINT legacy_status_check CHECK(status IN ('PENDING_PAYMENT','ACTIVE','CANCELLED')))");
                sql.execute("INSERT INTO partner_subscription VALUES (1,'PENDING_PAYMENT'),(2,'ACTIVE'),(3,'CANCELLED')");
                String migration = new ClassPathResource("db/migration/V20261106__partner_subscription_status.sql")
                        .getContentAsString(StandardCharsets.UTF_8);
                sql.execute(migration);
                sql.execute(migration);
                try (var rows = sql.executeQuery("SELECT status FROM partner_subscription ORDER BY id")) {
                    List<String> statuses = new ArrayList<>();
                    while (rows.next()) statuses.add(rows.getString(1));
                    assertThat(statuses).containsExactly("INACTIVE", "ACTIVE", "INACTIVE");
                }
                assertThatThrownBy(() -> sql.execute("INSERT INTO partner_subscription VALUES (4,'CANCELLED')"))
                        .isInstanceOf(java.sql.SQLException.class)
                        .satisfies(e -> assertThat(((java.sql.SQLException) e).getSQLState()).isEqualTo("23514"));
            } finally {
                sql.execute("ROLLBACK");
                connection.setSchema("public");
                sql.execute("DROP SCHEMA " + schema + " CASCADE");
            }
        }
    }

    private Long submit() {
        return orderService.acceptOrder(GeoOrderRequest.from("https://example.com", "postgres-site", "education"));
    }

    private void authenticate() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(clientId.toString(), "", List.of()));
    }
}
