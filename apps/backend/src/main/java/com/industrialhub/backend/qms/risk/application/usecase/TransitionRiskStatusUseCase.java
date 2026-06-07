package com.industrialhub.backend.qms.risk.application.usecase;

import com.industrialhub.backend.common.application.AuditService;
import com.industrialhub.backend.common.domain.AuditAction;
import com.industrialhub.backend.qms.risk.application.dto.RiskItemResponse;
import com.industrialhub.backend.qms.risk.application.dto.UpdateRiskStatusRequest;
import com.industrialhub.backend.qms.risk.domain.InvalidRiskStatusTransitionException;
import com.industrialhub.backend.qms.risk.domain.MitigationStatus;
import com.industrialhub.backend.qms.risk.domain.RiskItem;
import com.industrialhub.backend.qms.risk.domain.RiskItemNotFoundException;
import com.industrialhub.backend.qms.risk.domain.RiskLevel;
import com.industrialhub.backend.qms.risk.domain.RiskStatus;
import com.industrialhub.backend.qms.risk.infrastructure.RiskItemRepository;
import com.industrialhub.backend.qms.risk.infrastructure.RiskMitigationActionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
public class TransitionRiskStatusUseCase {

    private final RiskItemRepository riskItemRepository;
    private final RiskMitigationActionRepository mitigationRepository;
    private final AuditService auditService;

    public TransitionRiskStatusUseCase(RiskItemRepository riskItemRepository,
                                        RiskMitigationActionRepository mitigationRepository,
                                        AuditService auditService) {
        this.riskItemRepository = riskItemRepository;
        this.mitigationRepository = mitigationRepository;
        this.auditService = auditService;
    }

    @Transactional
    public RiskItemResponse execute(UUID id, UpdateRiskStatusRequest req, String principal) {
        RiskItem item = riskItemRepository.findById(id)
            .orElseThrow(() -> new RiskItemNotFoundException(id));

        RiskStatus current = item.getStatus();
        RiskStatus target = req.status();

        validateTransition(item, current, target);

        item.setStatus(target);
        RiskItem saved = riskItemRepository.save(item);

        auditService.log(principal, AuditAction.RISK_STATUS_CHANGED, "RiskItem", saved.getId(),
            Map.of("from", current.name(), "to", target.name()));

        return RiskItemResponse.from(saved);
    }

    private void validateTransition(RiskItem item, RiskStatus current, RiskStatus target) {
        boolean validTransition = switch (current) {
            case IDENTIFIED -> target == RiskStatus.BEING_MITIGATED;
            case BEING_MITIGATED -> target == RiskStatus.MITIGATED;
            case MITIGATED -> target == RiskStatus.ACCEPTED;
            case ACCEPTED -> false;
        };

        if (!validTransition) {
            throw new InvalidRiskStatusTransitionException(current, target);
        }

        // ISO 14971 §6: CRITICAL risks cannot be accepted without completed mitigation with residualRpn <= 100
        if (target == RiskStatus.ACCEPTED && item.getRiskLevel() == RiskLevel.CRITICAL) {
            boolean hasCompletedMitigationWithResidualBelow100 =
                mitigationRepository.existsByRiskItemIdAndStatusAndResidualRpnLessThanEqual(
                    item.getId(), MitigationStatus.COMPLETED, 100);

            if (!hasCompletedMitigationWithResidualBelow100) {
                throw new InvalidRiskStatusTransitionException(
                    "Riscos críticos devem ser mitigados antes de aceitar");
            }
        }
    }
}
