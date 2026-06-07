package com.industrialhub.backend.qms.risk.application.usecase;

import com.industrialhub.backend.qms.risk.application.dto.MitigationActionResponse;
import com.industrialhub.backend.qms.risk.application.dto.RiskItemDetailResponse;
import com.industrialhub.backend.qms.risk.domain.RiskItem;
import com.industrialhub.backend.qms.risk.domain.RiskItemNotFoundException;
import com.industrialhub.backend.qms.risk.infrastructure.RiskItemRepository;
import com.industrialhub.backend.qms.risk.infrastructure.RiskMitigationActionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class GetRiskItemDetailUseCase {

    private final RiskItemRepository riskItemRepository;
    private final RiskMitigationActionRepository mitigationRepository;

    public GetRiskItemDetailUseCase(RiskItemRepository riskItemRepository,
                                     RiskMitigationActionRepository mitigationRepository) {
        this.riskItemRepository = riskItemRepository;
        this.mitigationRepository = mitigationRepository;
    }

    @Transactional(readOnly = true)
    public RiskItemDetailResponse execute(UUID id) {
        RiskItem item = riskItemRepository.findById(id)
            .orElseThrow(() -> new RiskItemNotFoundException(id));

        List<MitigationActionResponse> actions = mitigationRepository
            .findByRiskItemId(id)
            .stream()
            .map(MitigationActionResponse::from)
            .toList();

        return RiskItemDetailResponse.from(item, actions);
    }
}
