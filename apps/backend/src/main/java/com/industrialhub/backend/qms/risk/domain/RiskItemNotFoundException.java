package com.industrialhub.backend.qms.risk.domain;

import java.util.UUID;

public class RiskItemNotFoundException extends RuntimeException {
    public RiskItemNotFoundException(UUID id) {
        super("Risk item não encontrado: " + id);
    }
}
