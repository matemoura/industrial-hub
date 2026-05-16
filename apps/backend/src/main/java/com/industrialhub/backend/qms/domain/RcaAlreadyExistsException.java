package com.industrialhub.backend.qms.domain;

public class RcaAlreadyExistsException extends RuntimeException {

    public RcaAlreadyExistsException() {
        super("Esta NC já possui uma análise de causa raiz");
    }
}
