package com.malgeum.geo.domain.pricing.academy.dto;

import com.malgeum.geo.domain.pricing.academy.entity.Academy;

public record AcademyRegisterRequest(String name, String domain, Academy.SizeType sizeType) {
}
