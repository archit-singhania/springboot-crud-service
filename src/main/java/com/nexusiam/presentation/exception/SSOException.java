package com.nexusiam.presentation.exception;

import com.nexusiam.presentation.exception.base.ExchangeException;
import com.nexusiam.presentation.exception.base.ExchangeError;
import com.nexusiam.shared.constants.SSOErrorCode;
import org.springframework.http.HttpStatus;

public class SSOException extends ExchangeException {

    private String requestId;

    public SSOException(SSOErrorCode errorCode) {
        super(errorCode);
    }

    public SSOException(ExchangeError error) {
        super(new ExchangeError() {
            @Override
            public String getErrorCode() {
                return error.getErrorCode();
            }
            @Override
            public HttpStatus getStatus() {
                return error.getStatus();
            }
            @Override
            public String getErrorMsg() {
                return error.getErrorMsg();
            }
        });
    }

    public SSOException(String message) {
        super(message);
    }

    public SSOException(String message, Throwable cause) {
        super(message, cause);
    }

    public SSOException(ExchangeError error, Throwable cause) {
        super(error, cause);
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }
}
