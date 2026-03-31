package com.nexusiam.presentation.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SSOErrorResponse {

    private String errorCode;
    private String message;
    private String requestId;
    private LocalDateTime timestamp;
    private String path;
    private Integer statusCode;
    private String details;

    public static SSOErrorResponse from(SSOException exception, String path, Integer statusCode) {
        return SSOErrorResponse.builder()
                .errorCode(exception.getCode())
                .message(exception.getMessage())
                .requestId(exception.getRequestId())
                .timestamp(LocalDateTime.now())
                .path(path)
                .statusCode(statusCode)
                .build();
    }
}
