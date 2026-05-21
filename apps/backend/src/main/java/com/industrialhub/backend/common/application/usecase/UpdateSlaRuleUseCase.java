package com.industrialhub.backend.common.application.usecase;

import com.industrialhub.backend.common.application.AuditService;
import com.industrialhub.backend.common.application.dto.SlaRuleResponse;
import com.industrialhub.backend.common.application.dto.UpdateSlaRuleRequest;
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
public class UpdateSlaRuleUseCase {

    private final SlaRuleRepository slaRuleRepository;
    private final AuditService auditService;

    @Transactional
    public SlaRuleResponse execute(UUID id, UpdateSlaRuleRequest request, String updatedBy) {
        SlaRule rule = slaRuleRepository.findById(id)
            .filter(SlaRule::isActive)
            .orElseThrow(() -> new SlaRuleNotFoundException(id));

        rule.setSlaHours(request.slaHours());
        rule.setEscalateByEmail(request.escalateByEmail());

        SlaRule saved = slaRuleRepository.save(rule);
        auditService.log(updatedBy, AuditAction.SLA_RULE_UPDATED, "SlaRule",
            saved.getId().toString(),
            Map.of("slaHours", saved.getSlaHours(), "escalateByEmail", saved.isEscalateByEmail()));

        return SlaRuleResponse.from(saved);
    }
}
