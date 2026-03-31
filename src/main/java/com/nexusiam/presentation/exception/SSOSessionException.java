package com.nexusiam.presentation.exception;

import com.nexusiam.shared.constants.SSOErrorCode;

public class SSOSessionException extends SSOException {

    public SSOSessionException(SSOErrorCode errorCode) {
        super(errorCode);
    }

    public SSOSessionException(String message) {
        super(message);
    }

    public SSOSessionException(String message, Throwable cause) {
        super(message, cause);
    }
}
