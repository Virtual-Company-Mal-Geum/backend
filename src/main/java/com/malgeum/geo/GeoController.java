package com.malgeum.geo;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.malgeum.geo.domain.domain.ReportResult;
import com.malgeum.geo.domain.domain.Order.CategoryStatus;
import com.malgeum.geo.global.ClientUpdateForm;
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

    public record GeoEvaluationRequest(String targetUrl,CategoryStatus categoryStatus) {
    }

    public record GeoEvaluationResponse(String message, Long orderId) {
    }

    @GetMapping("/")
    public ResponseEntity<String> root(){
        return ResponseEntity.ok("redirect:/home");
    }
    
    //추출한 페이지 구조 분석 요청 (프론트->백엔드)
    @PostMapping("/analyze")
    public ResponseEntity<GeoEvaluationResponse> startAnalysis(@RequestBody GeoEvaluationRequest request) {
        Long orderId = orderService.acceptOrder(request.targetUrl(),request.categoryStatus());
        return ResponseEntity.accepted()
                .body(new GeoEvaluationResponse("GEO 분석 요청이 성공적으로 접수되었습니다.", orderId));
    }

    //TODO:분석 완료 후, AI서버로부터 온 결과물 받고 처리 (AI서버->)

    //AI 분석 결과 조회 (백엔드->프론트)
    @GetMapping("/report/{orderId}")
    public ResponseEntity<ReportResult> getReport(@PathVariable Long orderId) {
        ReportResult report = reportService.getReportDetails(orderId);
        return ResponseEntity.ok(report);
    }

    //회원가입
    @GetMapping("/signup")
    public ResponseEntity<String> signUp(SignUpForm form){
        return ResponseEntity.ok("geo-signup");
    }

    @PostMapping("/signUp")
    public ResponseEntity<?> signup(@RequestBody SignUpForm form,BindingResult bindingResult){
        authService.signUp(form,bindingResult);
        return ResponseEntity.ok().build();
    }

    //로그인
    @GetMapping("/login")
    public ResponseEntity<String> login() {
        return ResponseEntity.ok("geo-login");
    }

    // @GetMapping("/change_password")
    // public ResponseEntity<String> changePassword(@RequestBody ClientUpdateForm clientUpdateForm){
    //     return ResponseEntity.ok("geo-change_password");
    // }
}
