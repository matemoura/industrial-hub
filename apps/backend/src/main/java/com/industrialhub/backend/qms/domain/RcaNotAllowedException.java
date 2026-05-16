package com.industrialhub.backend.qms.domain;

public class RcaNotAllowedException extends RuntimeException {

    public RcaNotAllowedException(String message) {
        super(message);
    }
}
