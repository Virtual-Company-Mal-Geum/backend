package com.malgeum.geo.dto;

//Response (AI Server -> Spring)
//success || error , AI evalutaion result
public record GeoEvaluationResponse(String status, String result) {
}
