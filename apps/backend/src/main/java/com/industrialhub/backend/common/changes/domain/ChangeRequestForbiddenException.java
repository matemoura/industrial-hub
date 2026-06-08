package com.industrialhub.backend.common.changes.domain;

public class ChangeRequestForbiddenException extends RuntimeException {
    public ChangeRequestForbiddenException(String message) {
        super(message);
    }
}
