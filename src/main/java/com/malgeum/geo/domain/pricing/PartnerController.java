package com.malgeum.geo.domain.pricing;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.malgeum.geo.domain.pricing.academy.dto.AcademyRegisterRequest;
import com.malgeum.geo.domain.pricing.academy.dto.AcademyRegisterResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/geo/partner")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class PartnerController {
    private final AcademyService academyService;

    //TODO: 추후 Partner제 재구독 시, 이전의 academy 목록을 불러오는 기능 구상 필요
    @PostMapping("/register_academy")
    public ResponseEntity<AcademyRegisterResponse> registerAcademy(@RequestBody AcademyRegisterRequest request) {
        Long academyId = academyService.registerAcademy(request);
        return ResponseEntity.ok(new AcademyRegisterResponse("학원이 등록되었습니다.", academyId));
    }
}
