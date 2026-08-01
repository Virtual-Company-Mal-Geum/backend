package com.malgeum.geo;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
import com.malgeum.geo.domain.domain.client.dto.ClientProfileResponse;
import com.malgeum.geo.domain.domain.client.service.ClientService;
import com.malgeum.geo.domain.domain.order.dto.GeoOrderRequest;
import com.malgeum.geo.domain.domain.order.dto.GeoOrderResponse;
import com.malgeum.geo.domain.domain.order.service.OrderService;
import com.malgeum.geo.domain.domain.order.service.OrderService.OrderSummaryResponse;
import com.malgeum.geo.dto.PasswordUpdateRequest;
import com.malgeum.geo.service.AuthService;
import com.malgeum.geo.service.GeoAsyncWorker;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/geo")
@RequiredArgsConstructor
public class GeoController {
    private final OrderService orderService;
    private final AnalysisReportService reportService;
    private final AuthService authService;
    private final GeoAsyncWorker geoAsyncWorker;
    private final ClientService clientService;

    @GetMapping("/orders")
    public ResponseEntity<List<OrderSummaryResponse>> getOrders(@AuthenticationPrincipal UserDetails userDetails) {
        Long clientId = Long.valueOf(userDetails.getUsername());
        return ResponseEntity.ok(orderService.getOrders(clientId));
    }

    @PostMapping("/order")
    public ResponseEntity<GeoOrderResponse> startAnalysis(@RequestBody GeoOrderRequest orderRequest) {
        Long orderId = orderService.acceptOrder(orderRequest);
        geoAsyncWorker.processSynchronously(orderId);
        return ResponseEntity.ok(new GeoOrderResponse("GEO 분석이 완료되었습니다.", orderId));
    }

    @GetMapping("/report/{orderId}")
    public ResponseEntity<ReportResult> getReport(@PathVariable("orderId") Long orderId) {
        ReportResult report = reportService.getReportDetails(orderId);
        return ResponseEntity.ok(report);
    }

    @PostMapping("/report/delete/{orderId}")
    public ResponseEntity<Void> deleteReport(@PathVariable("orderId") Long orderId) {
        reportService.deleteReport(orderId);
        return ResponseEntity.noContent().build();
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

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/profile")
    public ResponseEntity<ClientProfileResponse> profile(@AuthenticationPrincipal UserDetails userDetails) {
        Long clientId = Long.valueOf(userDetails.getUsername());
        return ResponseEntity.ok(clientService.getProfile(clientId));
    }

    @GetMapping("/change_password")
    public ResponseEntity<String> changePassword() {
        return ResponseEntity.ok("geo-change_password");
    }

    @PostMapping("/change_password")
    public ResponseEntity<Void> changePassword(@AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody PasswordUpdateRequest request) {
        authService.changePassword(Long.valueOf(userDetails.getUsername()),request);
        return ResponseEntity.noContent().build();
    }
}
