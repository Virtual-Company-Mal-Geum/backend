package com.malgeum.geo.domain.domain.analysisreport.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import com.malgeum.geo.domain.domain.analysisjob.entity.AnalysisJob;
import com.malgeum.geo.domain.domain.analysisjob.repository.AnalysisJobRepository;
import com.malgeum.geo.domain.domain.analysisreport.entity.AnalysisReport;
import com.malgeum.geo.domain.domain.analysisreport.entity.AnalysisReport.ReportStatus;
import com.malgeum.geo.domain.domain.analysisreport.entity.ReportResult;
import com.malgeum.geo.domain.domain.analysisreport.repository.AnalysisReportRepository;
import com.malgeum.geo.domain.domain.order.entity.Order;
import com.malgeum.geo.domain.domain.order.repository.OrderRepository;
import com.malgeum.geo.global.common.DataNotFoundException;

import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnalysisReportService {
    private final OrderRepository orderRepository;
    private final AnalysisJobRepository analysisJobRepository;
    private final AnalysisReportRepository analysisReportRepository;

    // 프론트에게 제공할 AI 분석 결과 상세 조회 메서드 (분석이 안 끝났어도 진행 상태는 보여준다)
    @Transactional(readOnly = true)
    public ReportResult getReportDetails(Long orderId) {
        // 고객사에서 요청한 분석 결과인지를 검수하기 위해 현재 로그인한 고객사 ID와 주문서의 고객사 ID가 일치하는지 확인
        Long currentClientId = Long.valueOf(SecurityContextHolder.getContext().getAuthentication().getName());
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("유효하지 않은 주문입니다."));

        if (!order.getClient().getId().equals(currentClientId)) {
            throw new IllegalArgumentException("해당 주문에 대한 접근 권한이 없습니다.");
        }

        AnalysisJob job = analysisJobRepository.findByOrderId(orderId)
                .orElseThrow(() -> new DataNotFoundException("AnalysisJob not found. orderId=" + orderId));

        Map<String, Object> aiResult = analysisReportRepository.findByOrderId(orderId)
                .map(AnalysisReport::getRawAILog) // AI출력물 JSON 파일, 분석 완료 전에는 없음
                .orElse(null);

        return new ReportResult(order.getId(),
                order.getTargetUrl(),
                job.getStatus().toExternal().name(),
                aiResult,
                job.getErrorMessage(),
                order.getCreatedAt());
    }

    @Transactional(readOnly = true)
    public List<ReportSummaryResponse> getReports(Long clientId) {
        List<Order> orders = orderRepository.findAllByClient_IdOrderByCreatedAtDesc(clientId);
        List<Long> orderIds = orders.stream().map(Order::getId).toList();

        Map<Long, AnalysisJob> jobsByOrderId = analysisJobRepository.findAllByOrder_IdIn(orderIds).stream()
                .collect(Collectors.toMap(job -> job.getOrder().getId(), job -> job));
        Map<Long, AnalysisReport> reportsByOrderId = analysisReportRepository.findAllByOrder_IdIn(orderIds).stream()
                .collect(Collectors.toMap(report -> report.getOrder().getId(), report -> report));

        return orders.stream()
                .filter(order -> {
                    AnalysisReport report = reportsByOrderId.get(order.getId());
                    return report == null || report.getReportStatus() != ReportStatus.DELETED;
                })
                .filter(order -> {
                    boolean hasJob = jobsByOrderId.containsKey(order.getId());
                    if (!hasJob) {
                        // AnalysisJob 도입 이전에 생성된 레거시 주문 등 정합성이 깨진 데이터 방어
                        log.warn("[AnalysisReportService] orderId={}에 대응하는 AnalysisJob이 없어 목록에서 제외합니다.",
                                order.getId());
                    }
                    return hasJob;
                })
                .map(order -> ReportSummaryResponse.from(order, jobsByOrderId.get(order.getId())))
                .toList();
    }

    @Transactional
    public void deleteReport(Long orderId) {
        AnalysisReport report = analysisReportRepository.findByOrderId(orderId)
                .orElseThrow(() -> new DataNotFoundException("Order not found. id=" + orderId));
        report.markDeleted();
    }

    public record ReportSummaryResponse(
            Long orderId,
            String siteName,
            String targetUrl,
            String domainStatus,
            String jobStatus,
            LocalDateTime createdAt) {
        public static ReportSummaryResponse from(Order order, AnalysisJob job) {
            return new ReportSummaryResponse(
                    order.getId(),
                    order.getSiteName(),
                    order.getTargetUrl(),
                    order.getDomainStatus().name(),
                    job.getStatus().toExternal().name(),
                    order.getCreatedAt());
        }
    }
}
