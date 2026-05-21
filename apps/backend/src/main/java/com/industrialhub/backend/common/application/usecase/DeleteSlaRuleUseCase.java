package com.industrialhub.backend.common.application.usecase;

import com.industrialhub.backend.common.application.AuditService;
import com.industrialhub.backend.common.domain.AuditAction;
import com.industrialhub.backend.common.domain.SlaRule;
import com.industrialhub.backend.common.domain.SlaRuleNotFoundException;
import com.industrialhub.backend.common.infrastructure.SlaRuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DeleteSlaRuleUseCase {

    private final SlaRuleRepository slaRuleRepository;
    private final AuditService auditService;

    @Transactional
    public void execute(UUID id, String deletedBy) {
        SlaRule rule = slaRuleRepository.findById(id)
            .filter(SlaRule::isActive)
            .orElseThrow(() -> new SlaRuleNotFoundException(id));

        rule.setActive(false);
        slaRuleRepository.save(rule);

        auditService.log(deletedBy, AuditAction.SLA_RULE_DELETED, "SlaRule",
            id.toString(),
            Map.of("entityType", rule.getEntityType().name(),
                   "classifierValue", rule.getClassifierValue()));
    }
}
