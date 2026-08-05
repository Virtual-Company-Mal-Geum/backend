package com.malgeum.geo.domain.domain.analysisjob.service;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.malgeum.geo.domain.domain.analysisjob.entity.AnalysisJob;
import com.malgeum.geo.domain.domain.analysisjob.repository.AnalysisJobRepository;
import com.malgeum.geo.domain.domain.order.entity.Order;
import com.malgeum.geo.global.common.DataNotFoundException;

@Service
@lombok.RequiredArgsConstructor
public class AnalysisJobService {
    private final AnalysisJobRepository analysisJobRepository;

    @Transactional
    public void enqueue(Order order) {
        analysisJobRepository.save(AnalysisJob.create(order));
    }

    @Transactional
    public Optional<ClaimedJob> claimNextJob() {
        Optional<AnalysisJob> _job = analysisJobRepository.findNextJobForUpdate();

        if (_job.isEmpty()) {
            return Optional.empty();
        }

        AnalysisJob job = _job.get();

        job.markRunning();

        return Optional.of(new ClaimedJob(job.getId(), job.getOrder().getId()));
    }

    public AnalysisJob getAnalysisJob(Long jobId) {
        return analysisJobRepository.findById(jobId)
                .orElseThrow(() -> new DataNotFoundException("AnalysisJob not found. id=" + jobId));
    }

    @Transactional
    public void markProcessing(Long orderId) {
        AnalysisJob job = analysisJobRepository.findByOrderId(orderId)
                .orElseThrow(() -> new DataNotFoundException("Order not found. id=" + orderId));
        job.markRunning();
    }

    @Transactional
    public void markSucceeded(Long orderId) {
        AnalysisJob job = analysisJobRepository.findByOrderId(orderId)
                .orElseThrow(() -> new DataNotFoundException("Order not found. id=" + orderId));
        job.markSucceeded();
    }

    @Transactional
    public void markFailureOrRetry(Long orderId, Exception e) {
        AnalysisJob job = analysisJobRepository.findByOrderId(orderId)
                .orElseThrow(() -> new DataNotFoundException("Order not found. id=" + orderId));

        if (job.canRetry()) {
            job.markRetryWait(e.getMessage(), LocalDateTime.now().plusSeconds(10));
            return;
        }

        job.markFailed(e.getMessage());
    }

    public record ClaimedJob(Long jobId, Long orderId) {
    }
}
