package com.malgeum.geo.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

//Response (AI Server -> Spring)
//status: success | insufficient_content | parse_error | error(=Spring 내부에서 합성한 통신/HTTP 오류)
//채점 결과 위치: /evaluate는 result, /diagnose는 analysis(+ 개선안 jsonld)
public record GeoEvaluationResponse(
        @JsonProperty("status") String status,
        @JsonProperty("domain") String domain,
        @JsonProperty("result") JsonNode result,
        @JsonProperty("reason") String reason,
        @JsonProperty("detail") String detail,
        @JsonProperty("content_warning") String contentWarning,
        @JsonProperty("main_content_length") Integer mainContentLength,
        @JsonProperty("cjk_normalized") Integer cjkNormalized,
        @JsonProperty("jsonld_input") JsonNode jsonldInput,
        @JsonProperty("analysis") JsonNode analysis,
        @JsonProperty("jsonld") JsonNode jsonld) {

    // /evaluate 응답용. analysis·jsonld는 /diagnose에만 있다.
    public GeoEvaluationResponse(String status, String domain, JsonNode result, String reason, String detail,
            String contentWarning, Integer mainContentLength, Integer cjkNormalized, JsonNode jsonldInput) {
        this(status, domain, result, reason, detail, contentWarning, mainContentLength, cjkNormalized, jsonldInput,
                null, null);
    }
}
