package com.malgeum.geo.dto;

import com.fasterxml.jackson.databind.JsonNode;

//Response (AI Server -> Spring)
//"success" || "error" , "text" , AI evalutaion content
public record GeoEvaluationResponse(
                String status,
                String domain,
                JsonNode result,
                String reason,
                String detail) {
}
