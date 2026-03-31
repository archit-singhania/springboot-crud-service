package com.nexusiam.presentation.exception.handler;

import com.nexusiam.presentation.exception.SSOException;
import com.nexusiam.presentation.exception.base.ApiError;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

@RestControllerAdvice
public class SSOExceptionHandler {

    @ExceptionHandler(SSOException.class)
    public ResponseEntity<ApiError> handleSSOException(SSOException ex) {

        ApiError error = ApiError.builder()
                .errorCode(ex.getCode())
                .message(ex.getMessage())
                .status(ex.getStatus())
                .timestamp(Instant.now())
                .build();

        return ResponseEntity
                .status(ex.getStatus())
                .body(error);
    }
}
