package com.industrialhub.backend.qms.audit.domain;

import java.util.UUID;

public class AuditChecklistItemNotFoundException extends RuntimeException {
    public AuditChecklistItemNotFoundException(UUID id) {
        super("Item de checklist não encontrado: " + id);
    }
}
