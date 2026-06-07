package com.industrialhub.backend.qms.risk.application.usecase;

import com.industrialhub.backend.qms.risk.application.dto.RiskItemResponse;
import com.industrialhub.backend.qms.risk.application.dto.UpdateRiskItemRequest;
import com.industrialhub.backend.qms.risk.domain.RiskItem;
import com.industrialhub.backend.qms.risk.domain.RiskItemNotFoundException;
import com.industrialhub.backend.qms.risk.infrastructure.RiskItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class UpdateRiskItemUseCase {

    private final RiskItemRepository riskItemRepository;

    public UpdateRiskItemUseCase(RiskItemRepository riskItemRepository) {
        this.riskItemRepository = riskItemRepository;
    }

    @Transactional
    public RiskItemResponse execute(UUID id, UpdateRiskItemRequest req) {
        RiskItem item = riskItemRepository.findById(id)
            .orElseThrow(() -> new RiskItemNotFoundException(id));

        item.setProcess(req.process());
        item.setFailureMode(req.failureMode());
        item.setFailureEffect(req.failureEffect());
        item.setFailureCause(req.failureCause());
        item.setSeverity(req.severity());
        item.setOccurrence(req.occurrence());
        item.setDetectability(req.detectability());
        item.setOwner(req.owner());
        item.setLinkedNcId(req.linkedNcId());
        item.setLinkedProductCode(req.linkedProductCode());

        item.recalculateRpn();

        RiskItem saved = riskItemRepository.save(item);
        return RiskItemResponse.from(saved);
    }
}
