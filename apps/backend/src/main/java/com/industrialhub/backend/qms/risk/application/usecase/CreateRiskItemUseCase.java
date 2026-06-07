package com.industrialhub.backend.qms.risk.application.usecase;

import com.industrialhub.backend.common.application.AuditService;
import com.industrialhub.backend.common.domain.AuditAction;
import com.industrialhub.backend.qms.risk.application.dto.CreateRiskItemRequest;
import com.industrialhub.backend.qms.risk.application.dto.RiskItemResponse;
import com.industrialhub.backend.qms.risk.domain.RiskItem;
import com.industrialhub.backend.qms.risk.domain.RiskStatus;
import com.industrialhub.backend.qms.risk.infrastructure.RiskItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreateRiskItemUseCase {

    private final RiskItemRepository riskItemRepository;
    private final AuditService auditService;

    public CreateRiskItemUseCase(RiskItemRepository riskItemRepository, AuditService auditService) {
        this.riskItemRepository = riskItemRepository;
        this.auditService = auditService;
    }

    @Transactional
    public RiskItemResponse execute(CreateRiskItemRequest req, String principal) {
        RiskItem item = RiskItem.builder()
            .process(req.process())
            .failureMode(req.failureMode())
            .failureEffect(req.failureEffect())
            .failureCause(req.failureCause())
            .severity(req.severity())
            .occurrence(req.occurrence())
            .detectability(req.detectability())
            .owner(req.owner())
            .linkedNcId(req.linkedNcId())
            .linkedProductCode(req.linkedProductCode())
            .status(RiskStatus.IDENTIFIED)
            .createdBy(principal)
            .build();

        item.recalculateRpn();

        RiskItem saved = riskItemRepository.save(item);
        auditService.log(principal, AuditAction.RISK_ITEM_CREATED, "RiskItem", saved.getId(), null);

        return RiskItemResponse.from(saved);
    }
}
