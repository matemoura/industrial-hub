package com.industrialhub.backend.qms.audit.domain;

import java.util.UUID;

public class InternalAuditNotFoundException extends RuntimeException {
    public InternalAuditNotFoundException(UUID id) {
        super("Auditoria não encontrada: " + id);
    }
}
