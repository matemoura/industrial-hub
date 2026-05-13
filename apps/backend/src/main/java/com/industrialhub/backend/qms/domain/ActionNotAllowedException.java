package com.industrialhub.backend.qms.domain;

public class ActionNotAllowedException extends RuntimeException {

    public ActionNotAllowedException(String message) {
        super(message);
    }
}
