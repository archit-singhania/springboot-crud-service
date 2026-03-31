package com.nexusiam.presentation.exception.base;

import org.springframework.http.HttpStatus;

public interface ExchangeError {
    String getErrorCode();
    HttpStatus getStatus();
    String getErrorMsg();
}
