package com.industrialhub.backend.qms.audit.domain;

public class AuditCodeAlreadyExistsException extends RuntimeException {
    public AuditCodeAlreadyExistsException(String code) {
        super("Já existe uma auditoria com o código: " + code);
    }
}
