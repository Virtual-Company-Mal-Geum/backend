package com.malgeum.geo.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

//Response (AI Server -> Spring)
//"success" || "error" , "text" , AI evalutaion content
public record GeoEvaluationResponse(String status, @JsonProperty("result_type") String resultType,
        String content) {
}
