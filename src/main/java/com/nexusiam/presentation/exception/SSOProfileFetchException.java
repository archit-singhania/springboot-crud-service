package com.nexusiam.presentation.exception;

import com.nexusiam.shared.constants.SSOErrorCode;

public class SSOProfileFetchException extends SSOException {

    public SSOProfileFetchException(SSOErrorCode errorCode) {
        super(errorCode);
    }

    public SSOProfileFetchException(String message) {
        super(message);
    }

    public SSOProfileFetchException(String message, Throwable cause) {
        super(message, cause);
    }
}
