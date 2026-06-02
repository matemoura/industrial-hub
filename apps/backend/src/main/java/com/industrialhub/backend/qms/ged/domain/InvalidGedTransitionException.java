package com.industrialhub.backend.qms.ged.domain;

public class InvalidGedTransitionException extends RuntimeException {
    public InvalidGedTransitionException(DocumentStatus from, DocumentStatus to) {
        super("Transição inválida: " + from + " → " + to);
    }

    public InvalidGedTransitionException(String message) {
        super(message);
    }
}
