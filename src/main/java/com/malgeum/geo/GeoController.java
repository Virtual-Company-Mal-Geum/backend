package com.malgeum.geo;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.malgeum.geo.service.OrderService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/geo")
@RequiredArgsConstructor
public class GeoController {
    private final OrderService orderService;

    public record GeoEvaluationRequest(String targetUrl) {
    }

    public record GeoEvaluationResponse(String message, Long orderId) {
    }

    @PostMapping("/analyze")
    public ResponseEntity<GeoEvaluationResponse> startAnalysis(@RequestBody GeoEvaluationRequest request) {
        Long orderId = orderService.acceptOrder(request.targetUrl());
        return ResponseEntity.accepted()
                .body(new GeoEvaluationResponse("GEO 분석 요청이 성공적으로 접수되었습니다.", orderId));
    }

    // @PostMapping("/login")
    // public ResponseEntity<String> login() {
    // }
}
