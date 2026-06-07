package com.industrialhub.backend.qms.risk.application.usecase;

import com.industrialhub.backend.qms.risk.application.dto.CreateMitigationActionRequest;
import com.industrialhub.backend.qms.risk.application.dto.MitigationActionResponse;
import com.industrialhub.backend.qms.risk.domain.MitigationStatus;
import com.industrialhub.backend.qms.risk.domain.RiskItem;
import com.industrialhub.backend.qms.risk.domain.RiskItemNotFoundException;
import com.industrialhub.backend.qms.risk.domain.RiskMitigationAction;
import com.industrialhub.backend.qms.risk.infrastructure.RiskItemRepository;
import com.industrialhub.backend.qms.risk.infrastructure.RiskMitigationActionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class CreateMitigationActionUseCase {

    private final RiskItemRepository riskItemRepository;
    private final RiskMitigationActionRepository mitigationRepository;

    public CreateMitigationActionUseCase(RiskItemRepository riskItemRepository,
                                          RiskMitigationActionRepository mitigationRepository) {
        this.riskItemRepository = riskItemRepository;
        this.mitigationRepository = mitigationRepository;
    }

    @Transactional
    public MitigationActionResponse execute(UUID riskItemId, CreateMitigationActionRequest req, String principal) {
        RiskItem riskItem = riskItemRepository.findById(riskItemId)
            .orElseThrow(() -> new RiskItemNotFoundException(riskItemId));

        RiskMitigationAction action = RiskMitigationAction.builder()
            .riskItem(riskItem)
            .description(req.description())
            .responsible(req.responsible())
            .targetDate(req.targetDate())
            .status(MitigationStatus.PLANNED)
            .createdBy(principal)
            .build();

        RiskMitigationAction saved = mitigationRepository.save(action);
        return MitigationActionResponse.from(saved);
    }
}
