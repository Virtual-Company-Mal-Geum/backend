package com.malgeum.geo.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

//Response (AI Server -> Spring)
//status: success | insufficient_content | parse_error | error(=Spring 내부에서 합성한 통신/HTTP 오류)
public record GeoEvaluationResponse(
        @JsonProperty("status") String status,
        @JsonProperty("domain") String domain,
        @JsonProperty("result") JsonNode result,
        @JsonProperty("reason") String reason,
        @JsonProperty("detail") String detail,
        @JsonProperty("content_warning") String contentWarning,
        @JsonProperty("main_content_length") Integer mainContentLength,
        @JsonProperty("cjk_normalized") Integer cjkNormalized,
        @JsonProperty("jsonld_input") JsonNode jsonldInput) {
}
