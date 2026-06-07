package com.industrialhub.backend.qms.risk.domain;

import java.util.UUID;

public class RiskMitigationActionNotFoundException extends RuntimeException {
    public RiskMitigationActionNotFoundException(UUID id) {
        super("Ação de mitigação não encontrada: " + id);
    }
}
