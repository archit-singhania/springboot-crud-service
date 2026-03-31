package com.nexusiam.presentation.exception;

import com.nexusiam.shared.constants.SSOErrorCode;

public class SSOAuthenticationException extends SSOException {

    public SSOAuthenticationException(SSOErrorCode errorCode) {
        super(errorCode);
    }

    public SSOAuthenticationException(String message) {
        super(message);
    }

    public SSOAuthenticationException(String message, Throwable cause) {
        super(message, cause);
    }
}
