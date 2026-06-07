package com.industrialhub.backend.qms.risk.application.usecase;

import com.industrialhub.backend.qms.risk.application.dto.RiskMatrixCell;
import com.industrialhub.backend.qms.risk.application.dto.RiskMatrixResponse;
import com.industrialhub.backend.qms.risk.domain.RiskLevel;
import com.industrialhub.backend.qms.risk.infrastructure.RiskItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class GetRiskMatrixUseCase {

    private final RiskItemRepository riskItemRepository;

    public GetRiskMatrixUseCase(RiskItemRepository riskItemRepository) {
        this.riskItemRepository = riskItemRepository;
    }

    @Transactional(readOnly = true)
    public RiskMatrixResponse execute() {
        List<Object[]> rows = riskItemRepository.findMatrixData();

        List<RiskMatrixCell> cells = rows.stream()
            .map(row -> new RiskMatrixCell(
                (Integer) row[0],
                (Integer) row[1],
                (Long) row[2],
                (RiskLevel) row[3]
            ))
            .toList();

        return new RiskMatrixResponse(cells);
    }
}
