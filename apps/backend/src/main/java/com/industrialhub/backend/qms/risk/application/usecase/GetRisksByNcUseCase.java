package com.industrialhub.backend.qms.risk.application.usecase;

import com.industrialhub.backend.qms.risk.application.dto.RiskItemSummary;
import com.industrialhub.backend.qms.risk.infrastructure.RiskItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class GetRisksByNcUseCase {

    private final RiskItemRepository riskItemRepository;

    public GetRisksByNcUseCase(RiskItemRepository riskItemRepository) {
        this.riskItemRepository = riskItemRepository;
    }

    @Transactional(readOnly = true)
    public List<RiskItemSummary> execute(UUID ncId) {
        return riskItemRepository.findByLinkedNcId(ncId)
            .stream()
            .map(RiskItemSummary::from)
            .toList();
    }
}
