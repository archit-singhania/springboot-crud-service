package com.nexusiam.presentation.exception.base;

import org.springframework.http.HttpStatus;

public class ExchangeException extends RuntimeException {

    private final ExchangeError error;

    public ExchangeException(ExchangeError error) {
        super(error.getErrorMsg());
        this.error = error;
    }

    public ExchangeException(ExchangeError error, Throwable cause) {
        super(error.getErrorMsg(), cause);
        this.error = error;
    }

    public ExchangeException(String message) {
        super(message);
        this.error = null;
    }

    public ExchangeException(String message, Throwable cause) {
        super(message, cause);
        this.error = null;
    }

    public String getCode() {
        return error != null ? error.getErrorCode() : "UNKNOWN";
    }

    public HttpStatus getStatus() {
        return error != null ? error.getStatus() : HttpStatus.INTERNAL_SERVER_ERROR;
    }

    public ExchangeError getError() {
        return error;
    }
}
