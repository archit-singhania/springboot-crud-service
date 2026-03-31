package com.nexusiam.application.service.validation;

public abstract class AbstractValidationHandler implements ValidationHandler {

    private ValidationHandler next;

    @Override
    public ValidationHandler setNext(ValidationHandler handler) {
        this.next = handler;
        return handler;
    }

    @Override
    public boolean validate(Object context) {
        if (doValidate(context)) {
            if (next != null) {
                return next.validate(context);
            }
            return true;
        }
        return false;
    }

    protected abstract boolean doValidate(Object context);
}
