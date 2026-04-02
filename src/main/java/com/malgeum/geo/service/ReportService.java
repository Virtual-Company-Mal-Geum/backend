package com.malgeum.geo.service;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import com.malgeum.geo.domain.domain.AnalysisReport;
import com.malgeum.geo.domain.domain.Order;
import com.malgeum.geo.domain.domain.ReportResult;
import com.malgeum.geo.global.common.AnalysisReportRepository;
import com.malgeum.geo.global.common.OrderRepository;

import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportService {
    private final OrderRepository orderRepository;
    private final AnalysisReportRepository analysisReportRepository;

    // 프론트에게 제공할 AI 분석 결과 상세 조회 메서드
    @Transactional(readOnly = true)
    public ReportResult getReportDetails(Long orderId) {
        // 고객사에서 요청한 분석 결과인지를 검수하기 위해 현재 로그인한 고객사 ID와 주문서의 고객사 ID가 일치하는지 확인
        Long currentClientId = Long.valueOf(SecurityContextHolder.getContext().getAuthentication().getName());
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("유효하지 않은 주문입니다."));

        if (!order.getClient().getId().equals(currentClientId)) {
            throw new IllegalArgumentException("해당 주문에 대한 접근 권한이 없습니다.");
        }

        AnalysisReport report = analysisReportRepository.findByOrder(order)
                .orElseThrow(() -> new IllegalArgumentException("아직 분석이 완료되지 않았습니다."));

        return new ReportResult(order.getId(),
                order.getTargetUrl(),
                order.getJobStatus().name(),
                report.getRawAILog(), // AI출력물 JSON 파일
                report.getCreatedAt());
    }
}
