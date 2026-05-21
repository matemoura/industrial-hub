package com.industrialhub.backend.common.domain;

import java.util.UUID;

public class SlaRuleNotFoundException extends RuntimeException {

    public SlaRuleNotFoundException(UUID id) {
        super("Regra SLA não encontrada: " + id);
    }
}
