package com.malgeum.geo;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import com.malgeum.geo.dto.GeoOrderRequest;
import com.malgeum.geo.dto.GeoOrderResponse;
import com.malgeum.geo.domain.domain.ReportResult;
import com.malgeum.geo.service.AuthService;
import com.malgeum.geo.service.OrderService;
import com.malgeum.geo.service.ReportService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/geo")
@RequiredArgsConstructor
public class GeoController {
    private final OrderService orderService;
    private final ReportService reportService;
    private final AuthService authService;

    @GetMapping("/orders")
    public ResponseEntity<List<Object>> getOrders() {
        return ResponseEntity.ok(List.of());
    }

    @PostMapping("/order")
    public ResponseEntity<GeoOrderResponse> startAnalysis(@RequestBody GeoOrderRequest orderRequest) {
        Long orderId = orderService.acceptOrder(orderRequest);
        return ResponseEntity.ok(new GeoOrderResponse("GEO 분석 요청이 접수되었습니다.", orderId));
    }

    @GetMapping("/report/{orderId}")
    public ResponseEntity<ReportResult> getReport(@PathVariable Long orderId) {
        ReportResult report = reportService.getReportDetails(orderId);
        return ResponseEntity.ok(report);
    }

    @GetMapping("/signup")
    public ResponseEntity<String> signUp(SignUpForm form) {
        return ResponseEntity.ok("geo-signup");
    }

    @PostMapping("/signup")
    public ResponseEntity<?> signup(@RequestBody SignUpForm form, BindingResult bindingResult) {
        authService.signup(form, bindingResult);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/login")
    public ResponseEntity<String> login() {
        return ResponseEntity.ok("geo-login");
    }
}
