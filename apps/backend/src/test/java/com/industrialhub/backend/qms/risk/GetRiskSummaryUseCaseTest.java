package com.industrialhub.backend.qms.risk;

import com.industrialhub.backend.qms.risk.application.dto.RiskSummary;
import com.industrialhub.backend.qms.risk.application.usecase.GetRiskSummaryUseCase;
import com.industrialhub.backend.qms.risk.domain.RiskItem;
import com.industrialhub.backend.qms.risk.domain.RiskLevel;
import com.industrialhub.backend.qms.risk.domain.RiskStatus;
import com.industrialhub.backend.qms.risk.infrastructure.RiskItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetRiskSummaryUseCaseTest {

    @Mock private RiskItemRepository riskItemRepository;

    private GetRiskSummaryUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GetRiskSummaryUseCase(riskItemRepository);
    }

    private RiskItem buildItem(RiskStatus status, RiskLevel riskLevel, int rpn) {
        RiskItem item = new RiskItem();
        item.setId(UUID.randomUUID());
        item.setProcess("Processo");
        item.setRpn(rpn);
        item.setRiskLevel(riskLevel);
        item.setStatus(status);
        item.setCreatedAt(LocalDateTime.now());
        item.setOwner("owner");
        item.setCreatedBy("user");
        item.setFailureMode("fm");
        item.setFailureEffect("fe");
        item.setFailureCause("fc");
        item.setSeverity(5);
        item.setOccurrence(5);
        item.setDetectability(5);
        return item;
    }

    @Test
    void shouldReturnCriticalCount2_whenTwoCriticalRisks() {
        // AC (c): criticalCount=2 quando 2 riscos CRITICAL
        when(riskItemRepository.count()).thenReturn(5L);
        when(riskItemRepository.countByRiskLevel(RiskLevel.CRITICAL)).thenReturn(2L);
        when(riskItemRepository.countByRiskLevel(RiskLevel.HIGH)).thenReturn(1L);
        when(riskItemRepository.countByRiskLevel(RiskLevel.MEDIUM)).thenReturn(1L);
        when(riskItemRepository.countByRiskLevel(RiskLevel.LOW)).thenReturn(1L);
        when(riskItemRepository.countByStatus(any())).thenReturn(0L);
        when(riskItemRepository.findAvgRpn()).thenReturn(150.0);
        when(riskItemRepository.findTop5ByStatusInOrderByRpnDesc(any())).thenReturn(List.of());

        RiskSummary summary = useCase.execute();

        assertThat(summary.criticalCount()).isEqualTo(2);
        assertThat(summary.totalRisks()).isEqualTo(5);
    }

    @Test
    void shouldReturnTopRisks_onlyIdentifiedOrBeingMitigated_orderedByRpnDesc() {
        // AC (d): topRisks retorna apenas IDENTIFIED ou BEING_MITIGATED, ordenados por RPN DESC
        RiskItem critical = buildItem(RiskStatus.IDENTIFIED, RiskLevel.CRITICAL, 250);
        RiskItem high = buildItem(RiskStatus.BEING_MITIGATED, RiskLevel.HIGH, 150);

        when(riskItemRepository.count()).thenReturn(2L);
        when(riskItemRepository.countByRiskLevel(any())).thenReturn(0L);
        when(riskItemRepository.countByStatus(any())).thenReturn(0L);
        when(riskItemRepository.findAvgRpn()).thenReturn(200.0);
        when(riskItemRepository.findTop5ByStatusInOrderByRpnDesc(
            List.of(RiskStatus.IDENTIFIED, RiskStatus.BEING_MITIGATED)))
            .thenReturn(List.of(critical, high));

        RiskSummary summary = useCase.execute();

        assertThat(summary.topRisks()).hasSize(2);
        assertThat(summary.topRisks().get(0).rpn()).isEqualTo(250);
        assertThat(summary.topRisks().get(1).rpn()).isEqualTo(150);
        assertThat(summary.topRisks().get(0).status()).isEqualTo(RiskStatus.IDENTIFIED);
        assertThat(summary.topRisks().get(1).status()).isEqualTo(RiskStatus.BEING_MITIGATED);
    }
}
