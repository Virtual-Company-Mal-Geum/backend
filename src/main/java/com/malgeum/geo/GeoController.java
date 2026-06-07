package com.malgeum.geo;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

import com.malgeum.geo.domain.domain.analysisreport.entity.ReportResult;
import com.malgeum.geo.domain.domain.analysisreport.service.AnalysisReportService;
import com.malgeum.geo.domain.domain.order.service.OrderService;
import com.malgeum.geo.domain.domain.order.service.OrderService.OrderSummaryResponse;
import com.malgeum.geo.dto.GeoOrderRequest;
import com.malgeum.geo.dto.GeoOrderResponse;
import com.malgeum.geo.service.AuthService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/geo")
@RequiredArgsConstructor
public class GeoController {
    private final OrderService orderService;
    private final AnalysisReportService reportService;
    private final AuthService authService;
    
    @GetMapping("/orders")
    public ResponseEntity<List<OrderSummaryResponse>> getOrders(@AuthenticationPrincipal UserDetails userDetails) {
        Long clientId = Long.valueOf(userDetails.getUsername());
        return ResponseEntity.ok(orderService.getOrders(clientId));
    }

    @PostMapping("/order")
    public ResponseEntity<GeoOrderResponse> startAnalysis(@RequestBody GeoOrderRequest orderRequest) {
        Long orderId = orderService.acceptOrder(orderRequest);
        return ResponseEntity.ok(new GeoOrderResponse("GEO 분석 요청이 접수되었습니다.", orderId));
    }

    @GetMapping("/report/{orderId}")
    public ResponseEntity<ReportResult> getReport(@PathVariable("orderId") Long orderId) {
        ReportResult report = reportService.getReportDetails(orderId);
        return ResponseEntity.ok(report);
    }

    @GetMapping("/signup")
    public ResponseEntity<String> signUp(SignUpForm form) {
        return ResponseEntity.ok("geo-signup");
    }

    @PostMapping("/signup")
    public ResponseEntity<Map<String, String>> signup(@RequestBody SignUpForm signUpForm, BindingResult bindingResult) {
        String token = authService.signup(signUpForm, bindingResult);
        return ResponseEntity.ok(Map.of(
                "token", token,
                "accessToken", token));
    }

    @GetMapping("/login")
    public ResponseEntity<String> login() {
        return ResponseEntity.ok("geo-login");
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, String>> login(@RequestBody LoginRequest request) {
        String token = authService.login(request);
        return ResponseEntity.ok(Map.of(
                "token", token,
                "accessToken", token));
    }
}
