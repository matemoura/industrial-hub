package com.industrialhub.backend.common.domain;

public class SelfAnonymizationException extends RuntimeException {
    public SelfAnonymizationException() {
        super("Não é possível anonimizar a própria conta");
    }
}
