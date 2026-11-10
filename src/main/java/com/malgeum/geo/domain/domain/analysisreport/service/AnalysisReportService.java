package com.malgeum.geo.domain.domain.analysisreport.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import com.malgeum.geo.domain.domain.analysisjob.entity.AnalysisJob;
import com.malgeum.geo.domain.domain.analysisjob.entity.AnalysisJobStatus;
import com.malgeum.geo.domain.domain.analysisjob.repository.AnalysisJobRepository;
import com.malgeum.geo.domain.domain.analysisreport.entity.AnalysisReport;
import com.malgeum.geo.domain.domain.analysisreport.entity.AnalysisReport.ReportStatus;
import com.malgeum.geo.domain.domain.analysisreport.entity.ReportResult;
import com.malgeum.geo.domain.domain.analysisreport.entity.ReportResult.Score;
import com.malgeum.geo.domain.domain.analysisreport.repository.AnalysisReportRepository;
import com.malgeum.geo.domain.domain.order.entity.Order;
import com.malgeum.geo.domain.domain.order.repository.OrderRepository;
import com.malgeum.geo.domain.domain.order.service.ReevaluationPolicy;
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

        // 원 주문과 재평가 주문을 한 묶음으로 조회한다. 어느 쪽 id로 조회해도 같은 versions가 나온다.
        Order baseOrder = order.isReevaluation() ? order.getParentOrder() : order;
        List<Order> group = new ArrayList<>();
        group.add(baseOrder);
        group.addAll(orderRepository.findAllByParentOrder_IdOrderByCreatedAtAsc(baseOrder.getId()));
        List<Long> groupIds = group.stream().map(Order::getId).toList();
        Map<Long, AnalysisJob> jobsByOrderId = jobsByOrderId(groupIds);
        Map<Long, AnalysisReport> reportsByOrderId = reportsByOrderId(groupIds);

        AnalysisJob job = jobsByOrderId.get(orderId);
        if (job == null) {
            throw new DataNotFoundException("AnalysisJob not found. orderId=" + orderId);
        }
        AnalysisReport report = reportsByOrderId.get(orderId);
        Map<String, Object> aiResult = report == null ? null : report.getRawAILog(); // AI출력물 JSON 파일, 분석 완료 전에는 없음

        List<ReportResult.Version> versions = group.stream()
                .filter(member -> jobsByOrderId.containsKey(member.getId()) && !isDeleted(reportsByOrderId.get(member.getId())))
                .map(member -> {
                    AnalysisJob memberJob = jobsByOrderId.get(member.getId());
                    return new ReportResult.Version(member.getId(),
                            member.getSiteName(),
                            memberJob.getStatus().toExternal().name(),
                            member.getCreatedAt(),
                            score(memberJob, reportsByOrderId.get(member.getId())));
                })
                .toList();

        // 재평가 권리는 개선안과 함께 요청한 원 주문에만 있다.
        Integer reevalRemaining = null;
        LocalDateTime reevalExpiresAt = null;
        if (baseOrder.isWithImprovement()) {
            List<AnalysisJob> reevaluationJobs = group.stream().skip(1)
                    .map(member -> jobsByOrderId.get(member.getId()))
                    .filter(Objects::nonNull)
                    .toList();
            reevalRemaining = ReevaluationPolicy.calculateRemainingReevaluationCount(reevaluationJobs);
            AnalysisJob baselineJob = jobsByOrderId.get(baseOrder.getId());
            if (baselineJob != null && baselineJob.getStatus() == AnalysisJobStatus.SUCCEEDED) {
                reevalExpiresAt = ReevaluationPolicy.expiresAt(baselineJob);
            }
        }

        return new ReportResult(order.getId(),
                order.getSiteName(),
                order.getTargetUrl(),
                job.getStatus().toExternal().name(),
                aiResult,
                job.getErrorMessage(),
                order.getCreatedAt(),
                order.isReevaluation() ? baseOrder.getId() : null,
                versions,
                reevalRemaining,
                reevalExpiresAt);
    }

    @Transactional(readOnly = true)
    public List<ReportSummaryResponse> getReports(Long clientId) {
        List<Order> orders = orderRepository.findAllByClient_IdOrderByCreatedAtDesc(clientId);
        List<Long> orderIds = orders.stream().map(Order::getId).toList();

        Map<Long, AnalysisJob> jobsByOrderId = jobsByOrderId(orderIds);
        Map<Long, AnalysisReport> reportsByOrderId = reportsByOrderId(orderIds);

        List<Order> visibleOrders = orders.stream()
                .filter(order -> !isDeleted(reportsByOrderId.get(order.getId())))
                .filter(order -> {
                    boolean hasJob = jobsByOrderId.containsKey(order.getId());
                    if (!hasJob) {
                        // AnalysisJob 도입 이전에 생성된 레거시 주문 등 정합성이 깨진 데이터 방어
                        log.warn("[AnalysisReportService] orderId={}에 대응하는 AnalysisJob이 없어 목록에서 제외합니다.",
                                order.getId());
                    }
                    return hasJob;
                })
                .toList();

        // 재평가 주문은 목록 행으로 만들지 않고 원 주문 행의 요약으로만 보여준다. (최신순 정렬 유지)
        Map<Long, List<Order>> reevaluationsByBaselineId = visibleOrders.stream()
                .filter(Order::isReevaluation)
                .collect(Collectors.groupingBy(order -> order.getParentOrder().getId()));

        return visibleOrders.stream()
                .filter(order -> !order.isReevaluation())
                .map(order -> {
                    List<Order> reevaluations = reevaluationsByBaselineId.getOrDefault(order.getId(), List.of());
                    LatestReeval latestReeval = reevaluations.isEmpty() ? null
                            : LatestReeval.from(reevaluations.get(0), jobsByOrderId, reportsByOrderId);
                    AnalysisJob job = jobsByOrderId.get(order.getId());
                    return ReportSummaryResponse.from(order, job,
                            score(job, reportsByOrderId.get(order.getId())),
                            reevaluations.size(),
                            latestReeval);
                })
                .toList();
    }

    private Map<Long, AnalysisJob> jobsByOrderId(List<Long> orderIds) {
        return analysisJobRepository.findAllByOrder_IdIn(orderIds).stream()
                .collect(Collectors.toMap(job -> job.getOrder().getId(), job -> job));
    }

    private Map<Long, AnalysisReport> reportsByOrderId(List<Long> orderIds) {
        return analysisReportRepository.findAllByOrder_IdIn(orderIds).stream()
                .collect(Collectors.toMap(report -> report.getOrder().getId(), report -> report));
    }

    private static boolean isDeleted(AnalysisReport report) {
        return report != null && report.getReportStatus() == ReportStatus.DELETED;
    }

    private static Score score(AnalysisJob job, AnalysisReport report) {
        return Score.from(job, report == null ? null : report.getRawAILog());
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
            LocalDateTime createdAt,
            Score score, // 완료 전이면 null
            int reevalCount, // 실패 건 포함
            LatestReeval latestReeval) { // 재평가가 없으면 null
        public static ReportSummaryResponse from(Order order, AnalysisJob job, Score score, int reevalCount,
                LatestReeval latestReeval) {
            return new ReportSummaryResponse(
                    order.getId(),
                    order.getSiteName(),
                    order.getTargetUrl(),
                    order.getDomainStatus().name(),
                    job.getStatus().toExternal().name(),
                    order.getCreatedAt(),
                    score,
                    reevalCount,
                    latestReeval);
        }
    }

    public record LatestReeval(Long orderId, String jobStatus, Score score) {
        static LatestReeval from(Order order, Map<Long, AnalysisJob> jobsByOrderId,
                Map<Long, AnalysisReport> reportsByOrderId) {
            AnalysisJob job = jobsByOrderId.get(order.getId());
            return new LatestReeval(order.getId(), job.getStatus().toExternal().name(),
                    AnalysisReportService.score(job, reportsByOrderId.get(order.getId())));
        }
    }
}
