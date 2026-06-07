package com.industrialhub.backend.qms.risk.application.usecase;

import com.industrialhub.backend.qms.risk.application.dto.RiskItemResponse;
import com.industrialhub.backend.qms.risk.domain.RiskLevel;
import com.industrialhub.backend.qms.risk.domain.RiskStatus;
import com.industrialhub.backend.qms.risk.infrastructure.RiskItemRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class GetRiskItemsUseCase {

    private final RiskItemRepository riskItemRepository;

    public GetRiskItemsUseCase(RiskItemRepository riskItemRepository) {
        this.riskItemRepository = riskItemRepository;
    }

    @Transactional(readOnly = true)
    public Page<RiskItemResponse> execute(RiskStatus status, RiskLevel riskLevel,
                                           String owner, UUID linkedNcId,
                                           String linkedProductCode, Pageable pageable) {
        return riskItemRepository
            .findByFilters(status, riskLevel, owner, linkedNcId, linkedProductCode, pageable)
            .map(RiskItemResponse::from);
    }
}
