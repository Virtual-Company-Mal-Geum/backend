package com.malgeum.geo.domain.domain.analysisjob.service;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.malgeum.geo.domain.domain.analysisjob.entity.AnalysisJob;
import com.malgeum.geo.domain.domain.analysisjob.repository.AnalysisJobRepository;
import com.malgeum.geo.domain.domain.order.entity.Order;
import com.malgeum.geo.domain.domain.order.repository.OrderRepository;
import com.malgeum.geo.global.common.DataNotFoundException;

@Service
@lombok.RequiredArgsConstructor
public class AnalysisJobService {
    private final AnalysisJobRepository analysisJobRepository;
    private final OrderRepository orderRepository;

    @Transactional
    public void enqueue(Order order) {
        analysisJobRepository.save(AnalysisJob.create(order));
    }

    @Transactional
    public Optional<ClaimedJob> claimNextJob() {
        Optional<AnalysisJob> _analysisJob = analysisJobRepository.findNextJobForUpdate();

        if (_analysisJob.isEmpty()) {
            return Optional.empty();
        }

        AnalysisJob analysisJob = _analysisJob.get();
        analysisJob.markRunning();
        analysisJob.getOrder().updateStatus(Order.OrderStatus.RUNNING);

        return Optional.of(new ClaimedJob(analysisJob.getId(), analysisJob.getOrder().getId()));
    }

    @Transactional(readOnly = true)
    public Order getOrderForProcessing(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new DataNotFoundException("Order not found. id=" + orderId));
    }

    @Transactional
    public void markSucceeded(Long jobId) {
        AnalysisJob job = analysisJobRepository.findById(jobId).orElseThrow();
        job.markSucceeded();
        job.getOrder().updateStatus(Order.OrderStatus.SUCCESS);
    }

    @Transactional
    public void markFailureOrRetry(Long jobId, Exception e) {
        AnalysisJob job = analysisJobRepository.findById(jobId)
                .orElseThrow();

        if (job.canRetry()) {
            job.markRetryWait(e.getMessage(), LocalDateTime.now().plusSeconds(10));
            job.getOrder().updateStatus(Order.OrderStatus.RETRY_WAIT);
        } else {
            job.markFailed(e.getMessage());
            job.getOrder().markFailed();
        }
    }

    public record ClaimedJob(Long jobId, Long orderId) {
    }
}
