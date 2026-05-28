package com.industrialhub.backend.production.domain;

public class InvalidLoadTransitionException extends RuntimeException {
    public InvalidLoadTransitionException(LoadStatus from, LoadStatus to) {
        super("Transição inválida: %s → %s".formatted(from, to));
    }
}
