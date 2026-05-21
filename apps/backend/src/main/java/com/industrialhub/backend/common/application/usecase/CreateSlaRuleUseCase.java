package com.industrialhub.backend.common.application.usecase;

import com.industrialhub.backend.common.application.AuditService;
import com.industrialhub.backend.common.application.dto.CreateSlaRuleRequest;
import com.industrialhub.backend.common.application.dto.SlaRuleResponse;
import com.industrialhub.backend.common.domain.AuditAction;
import com.industrialhub.backend.common.domain.SlaRule;
import com.industrialhub.backend.common.domain.SlaRuleDuplicateException;
import com.industrialhub.backend.common.infrastructure.SlaRuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CreateSlaRuleUseCase {

    private final SlaRuleRepository slaRuleRepository;
    private final AuditService auditService;

    @Transactional
    public SlaRuleResponse execute(CreateSlaRuleRequest request, String createdBy) {
        boolean exists = slaRuleRepository.existsByEntityTypeAndClassifierFieldAndClassifierValueAndActiveTrue(
            request.entityType(), request.classifierField(), request.classifierValue()
        );
        if (exists) {
            throw new SlaRuleDuplicateException();
        }

        SlaRule rule = SlaRule.builder()
            .entityType(request.entityType())
            .classifierField(request.classifierField())
            .classifierValue(request.classifierValue())
            .slaHours(request.slaHours())
            .escalateByEmail(request.escalateByEmail())
            .active(true)
            .createdAt(LocalDateTime.now())
            .build();

        SlaRule saved = slaRuleRepository.save(rule);
        auditService.log(createdBy, AuditAction.SLA_RULE_CREATED, "SlaRule",
            saved.getId().toString(),
            Map.of("entityType", saved.getEntityType().name(),
                   "classifierValue", saved.getClassifierValue(),
                   "slaHours", saved.getSlaHours()));

        return SlaRuleResponse.from(saved);
    }
}
