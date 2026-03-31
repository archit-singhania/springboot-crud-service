package com.nexusiam.presentation.exception;

import com.nexusiam.shared.constants.SSOErrorCode;

public class SSOProfileValidationException extends SSOException {

    public SSOProfileValidationException(SSOErrorCode errorCode) {
        super(errorCode);
    }

    public SSOProfileValidationException(String message) {
        super(message);
    }

    public SSOProfileValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
