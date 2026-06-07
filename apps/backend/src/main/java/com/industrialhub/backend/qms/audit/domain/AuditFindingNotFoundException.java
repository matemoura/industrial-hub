package com.industrialhub.backend.qms.audit.domain;

import java.util.UUID;

public class AuditFindingNotFoundException extends RuntimeException {
    public AuditFindingNotFoundException(UUID id) {
        super("Achado de auditoria não encontrado: " + id);
    }
}
