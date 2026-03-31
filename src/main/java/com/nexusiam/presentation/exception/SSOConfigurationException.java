package com.nexusiam.presentation.exception;

import com.nexusiam.shared.constants.SSOErrorCode;

public class SSOConfigurationException extends SSOException {

    public SSOConfigurationException(SSOErrorCode errorCode) {
        super(errorCode);
    }

    public SSOConfigurationException(String message) {
        super(message);
    }

    public SSOConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
