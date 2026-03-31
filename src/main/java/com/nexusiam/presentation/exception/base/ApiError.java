package com.nexusiam.presentation.exception.base;

import lombok.Builder;
import lombok.Data;
import org.springframework.http.HttpStatus;

import java.time.Instant;

@Data
@Builder
public class ApiError {
    private String errorCode;
    private HttpStatus status;
    private String message;
    private Instant timestamp;
    private String path;
}
