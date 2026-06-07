package com.industrialhub.backend.qms.risk.application.usecase;

import com.industrialhub.backend.qms.risk.application.dto.RiskItemSummary;
import com.industrialhub.backend.qms.risk.application.dto.RiskSummary;
import com.industrialhub.backend.qms.risk.domain.RiskLevel;
import com.industrialhub.backend.qms.risk.domain.RiskStatus;
import com.industrialhub.backend.qms.risk.infrastructure.RiskItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class GetRiskSummaryUseCase {

    private final RiskItemRepository riskItemRepository;

    public GetRiskSummaryUseCase(RiskItemRepository riskItemRepository) {
        this.riskItemRepository = riskItemRepository;
    }

    @Transactional(readOnly = true)
    public RiskSummary execute() {
        long totalRisks = riskItemRepository.count();
        long criticalCount = riskItemRepository.countByRiskLevel(RiskLevel.CRITICAL);
        long highCount = riskItemRepository.countByRiskLevel(RiskLevel.HIGH);
        long mediumCount = riskItemRepository.countByRiskLevel(RiskLevel.MEDIUM);
        long lowCount = riskItemRepository.countByRiskLevel(RiskLevel.LOW);

        Map<RiskStatus, Integer> byStatus = Arrays.stream(RiskStatus.values())
            .collect(Collectors.toMap(
                s -> s,
                s -> (int) riskItemRepository.countByStatus(s)
            ));

        Double avgRpn = totalRisks > 0 ? riskItemRepository.findAvgRpn() : null;

        List<RiskItemSummary> topRisks = riskItemRepository
            .findTop5ByStatusInOrderByRpnDesc(List.of(RiskStatus.IDENTIFIED, RiskStatus.BEING_MITIGATED))
            .stream()
            .map(RiskItemSummary::from)
            .toList();

        return new RiskSummary(totalRisks, criticalCount, highCount, mediumCount, lowCount,
            byStatus, avgRpn, topRisks);
    }
}
