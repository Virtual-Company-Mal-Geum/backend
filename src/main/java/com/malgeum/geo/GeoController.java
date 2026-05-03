package com.malgeum.geo;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.malgeum.geo.domain.domain.ReportResult;
import com.malgeum.geo.service.OrderService;
import com.malgeum.geo.service.ReportService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/geo")
@RequiredArgsConstructor
public class GeoController {
    private final OrderService orderService;
    private final ReportService reportService;

    public record GeoEvaluationRequest(String targetUrl) {
    }

    public record GeoEvaluationResponse(String message, Long orderId) {
    }

    @GetMapping("/")
    public ResponseEntity<String> root(){
        return ResponseEntity.ok("redirect:/home");
    }
    
    @PostMapping("/analyze")
    public ResponseEntity<GeoEvaluationResponse> startAnalysis(@RequestBody GeoEvaluationRequest request) {
        Long orderId = orderService.acceptOrder(request.targetUrl());
        return ResponseEntity.accepted()
                .body(new GeoEvaluationResponse("GEO 분석 요청이 성공적으로 접수되었습니다.", orderId));
    }

    @GetMapping("/report/{orderId}")
    public ResponseEntity<ReportResult> getReport(@PathVariable Long orderId) {
        ReportResult report = reportService.getReportDetails(orderId);
        return ResponseEntity.ok(report);
    }

    // @GetMapping("/login")
    // public ResponseEntity<String> login() {
    // }
}
