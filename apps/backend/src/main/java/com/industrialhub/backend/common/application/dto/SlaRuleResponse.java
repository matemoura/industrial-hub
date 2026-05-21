package com.industrialhub.backend.common.application.dto;

import com.industrialhub.backend.common.domain.SlaClassifierField;
import com.industrialhub.backend.common.domain.SlaEntityType;
import com.industrialhub.backend.common.domain.SlaRule;

import java.util.UUID;

public record SlaRuleResponse(
    UUID id,
    SlaEntityType entityType,
    SlaClassifierField classifierField,
    String classifierValue,
    int slaHours,
    boolean escalateByEmail,
    boolean active
) {
    public static SlaRuleResponse from(SlaRule rule) {
        return new SlaRuleResponse(
            rule.getId(),
            rule.getEntityType(),
            rule.getClassifierField(),
            rule.getClassifierValue(),
            rule.getSlaHours(),
            rule.isEscalateByEmail(),
            rule.isActive()
        );
    }
}
