package com.nexusiam.application.service.validation;

public interface ValidationHandler {

    ValidationHandler setNext(ValidationHandler handler);

    boolean validate(Object context);
}
