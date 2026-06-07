package com.industrialhub.backend.qms.risk.application.usecase;

import com.industrialhub.backend.common.application.AuditService;
import com.industrialhub.backend.common.domain.AuditAction;
import com.industrialhub.backend.qms.risk.application.dto.MitigationActionResponse;
import com.industrialhub.backend.qms.risk.application.dto.UpdateMitigationActionRequest;
import com.industrialhub.backend.qms.risk.domain.MitigationStatus;
import com.industrialhub.backend.qms.risk.domain.RiskMitigationAction;
import com.industrialhub.backend.qms.risk.domain.RiskMitigationActionNotFoundException;
import com.industrialhub.backend.qms.risk.infrastructure.RiskMitigationActionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class UpdateMitigationActionUseCase {

    private final RiskMitigationActionRepository mitigationRepository;
    private final AuditService auditService;

    public UpdateMitigationActionUseCase(RiskMitigationActionRepository mitigationRepository,
                                          AuditService auditService) {
        this.mitigationRepository = mitigationRepository;
        this.auditService = auditService;
    }

    @Transactional
    public MitigationActionResponse execute(UUID actionId, UpdateMitigationActionRequest req, String principal) {
        RiskMitigationAction action = mitigationRepository.findById(actionId)
            .orElseThrow(() -> new RiskMitigationActionNotFoundException(actionId));

        boolean wasCompleted = action.getStatus() == MitigationStatus.COMPLETED;

        action.setDescription(req.description());
        action.setResponsible(req.responsible());
        action.setTargetDate(req.targetDate());
        action.setCompletedAt(req.completedAt());
        action.setStatus(req.status());

        if (req.status() == MitigationStatus.COMPLETED
                && req.residualSeverity() != null
                && req.residualOccurrence() != null
                && req.residualDetectability() != null) {
            action.setResidualSeverity(req.residualSeverity());
            action.setResidualOccurrence(req.residualOccurrence());
            action.setResidualDetectability(req.residualDetectability());
            action.setResidualRpn(req.residualSeverity() * req.residualOccurrence() * req.residualDetectability());
        }

        RiskMitigationAction saved = mitigationRepository.save(action);

        if (!wasCompleted && req.status() == MitigationStatus.COMPLETED) {
            auditService.log(principal, AuditAction.MITIGATION_ACTION_COMPLETED,
                "RiskMitigationAction", saved.getId(), null);
        }

        return MitigationActionResponse.from(saved);
    }
}
