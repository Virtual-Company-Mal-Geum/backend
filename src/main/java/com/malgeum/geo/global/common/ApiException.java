package com.malgeum.geo.global.common;

import org.springframework.http.HttpStatus;

import lombok.Getter;

// 프론트가 message가 아닌 code로 분기하도록 { code, message } 응답을 내려주는 예외
@Getter
public class ApiException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    public ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }
}
