package com.nexusiam.presentation.exception;

import com.nexusiam.shared.constants.SSOErrorCode;

public class SSOTokenExchangeException extends SSOException {

    public SSOTokenExchangeException(SSOErrorCode errorCode) {
        super(errorCode);
    }

    public SSOTokenExchangeException(String message) {
        super(message);
    }

    public SSOTokenExchangeException(String message, Throwable cause) {
        super(message, cause);
    }
}
