package com.malgeum.geo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.malgeum.geo.domain.domain.analysisjob.entity.AnalysisJobStatus;
import com.malgeum.geo.domain.domain.analysisjob.repository.AnalysisJobRepository;
import com.malgeum.geo.domain.domain.analysisjob.service.AnalysisJobService;
import com.malgeum.geo.domain.domain.client.entity.Client;
import com.malgeum.geo.domain.domain.client.repository.ClientRepository;
import com.malgeum.geo.domain.domain.order.entity.Order;
import com.malgeum.geo.domain.domain.order.entity.Order.DomainStatus;
import com.malgeum.geo.domain.domain.order.entity.Order.OrderStatus;
import com.malgeum.geo.domain.domain.order.repository.OrderRepository;
import com.malgeum.geo.dto.GeoEvaluationResponse;
import com.malgeum.geo.dto.ScrapedData;

@SpringBootTest(properties = "spring.task.scheduling.enabled=false")
@ActiveProfiles("test")
class GeoAsyncWorkerQueueIntegrationTest {

    @Autowired
    private GeoAsyncWorker geoAsyncWorker;

    @Autowired
    private AnalysisJobService analysisJobService;

    @Autowired
    private AnalysisJobRepository analysisJobRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ClientRepository clientRepository;

    @MockitoBean
    private GeoScrapingService geoScrapingService;

    @MockitoBean
    private GeoAiService geoAiService;

    @Test
    @DisplayName("요청이 한 번에 몰려도 AI 서버 호출은 동시 1개로 순차 처리된다.")
    void shouldProcessAiRequestsSequentiallyWhenManyJobsAreQueued() throws Exception {
        Client client = clientRepository.save(Client.builder()
                .name("queue-test-user")
                .email("queue-test@example.com")
                .company("queue-test")
                .phone("010-0000-0000")
                .password("pw")
                .build());

        List<String> expectedUrls = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            String url = "https://example.com/" + i;
            Order order = orderRepository.save(Order.builder()
                    .client(client)
                    .targetUrl(url)
                    .siteName("site-" + i)
                    .serviceType("service")
                    .domainStatus(DomainStatus.ECOMMERCE)
                    .build());
            analysisJobService.enqueue(order);
            expectedUrls.add(url);
            Thread.sleep(5L);
        }

        given(geoScrapingService.extractDataForAi(any(String.class), any(DomainStatus.class)))
                .willAnswer(invocation -> {
                    String url = invocation.getArgument(0);
                    DomainStatus domain = invocation.getArgument(1);
                    return new ScrapedData(url, domain.toString(), "html-" + url, null, null);
                });

        AtomicInteger inFlight = new AtomicInteger(0);
        AtomicInteger maxInFlight = new AtomicInteger(0);
        List<String> calledUrls = new ArrayList<>();

        given(geoAiService.evaluateTarget(any()))
                .willAnswer(invocation -> {
                    int now = inFlight.incrementAndGet();
                    maxInFlight.getAndUpdate(prev -> Math.max(prev, now));
                    try {
                        String url = invocation.getArgument(0, com.malgeum.geo.dto.GeoEvaluationRequest.class).url();
                        calledUrls.add(url);
                        Thread.sleep(50L);
                        return new GeoEvaluationResponse("success", "text", "{\"score\":95}");
                    } finally {
                        inFlight.decrementAndGet();
                    }
                });

        geoAsyncWorker.pollAndProcess();

        assertThat(maxInFlight.get()).isEqualTo(1);
        assertThat(calledUrls).containsExactlyElementsOf(expectedUrls);
        assertThat(analysisJobRepository.findAll())
                .allMatch(job -> job.getStatus() == AnalysisJobStatus.SUCCEEDED);
        assertThat(orderRepository.findAll())
                .allMatch(order -> order.getJobStatus() == OrderStatus.SUCCESS);
    }
}
