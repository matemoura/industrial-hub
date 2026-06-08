package com.industrialhub.backend.common.changes.domain;

public class ChangeRequestCodeConflictException extends RuntimeException {
    public ChangeRequestCodeConflictException(String code) {
        super("Change request code already exists: " + code);
    }
}
