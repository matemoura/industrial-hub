package com.industrialhub.backend.qms.audit.domain;

public class InvalidAuditStatusTransitionException extends RuntimeException {
    public InvalidAuditStatusTransitionException(AuditStatus from, AuditStatus to) {
        super("Transição de status inválida: " + from + " → " + to);
    }

    public InvalidAuditStatusTransitionException(String message) {
        super(message);
    }
}
