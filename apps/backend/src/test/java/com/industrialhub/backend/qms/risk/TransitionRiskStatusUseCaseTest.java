package com.industrialhub.backend.qms.risk;

import com.industrialhub.backend.common.application.AuditService;
import com.industrialhub.backend.qms.risk.application.dto.UpdateRiskStatusRequest;
import com.industrialhub.backend.qms.risk.application.usecase.TransitionRiskStatusUseCase;
import com.industrialhub.backend.qms.risk.domain.InvalidRiskStatusTransitionException;
import com.industrialhub.backend.qms.risk.domain.MitigationStatus;
import com.industrialhub.backend.qms.risk.domain.RiskItem;
import com.industrialhub.backend.qms.risk.domain.RiskItemNotFoundException;
import com.industrialhub.backend.qms.risk.domain.RiskLevel;
import com.industrialhub.backend.qms.risk.domain.RiskStatus;
import com.industrialhub.backend.qms.risk.infrastructure.RiskItemRepository;
import com.industrialhub.backend.qms.risk.infrastructure.RiskMitigationActionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransitionRiskStatusUseCaseTest {

    @Mock private RiskItemRepository riskItemRepository;
    @Mock private RiskMitigationActionRepository mitigationRepository;
    @Mock private AuditService auditService;

    private TransitionRiskStatusUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new TransitionRiskStatusUseCase(riskItemRepository, mitigationRepository, auditService);
    }

    private RiskItem buildItem(RiskStatus status, RiskLevel riskLevel) {
        RiskItem item = new RiskItem();
        item.setId(UUID.randomUUID());
        item.setProcess("Processo");
        item.setFailureMode("Falha");
        item.setFailureEffect("Efeito");
        item.setFailureCause("Causa");
        item.setSeverity(5);
        item.setOccurrence(5);
        item.setDetectability(5);
        item.setRpn(125);
        item.setRiskLevel(riskLevel);
        item.setStatus(status);
        item.setOwner("owner");
        item.setCreatedBy("user");
        item.setCreatedAt(LocalDateTime.now());
        return item;
    }

    @Test
    void shouldTransitionFromIdentified_to_BeingMitigated() {
        // AC (a): IDENTIFIED→BEING_MITIGATED
        RiskItem item = buildItem(RiskStatus.IDENTIFIED, RiskLevel.HIGH);
        when(riskItemRepository.findById(item.getId())).thenReturn(Optional.of(item));
        when(riskItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var response = useCase.execute(item.getId(),
            new UpdateRiskStatusRequest(RiskStatus.BEING_MITIGATED), "supervisor");

        assertThat(response.status()).isEqualTo(RiskStatus.BEING_MITIGATED);
    }

    @Test
    void shouldTransitionFromBeingMitigated_to_Mitigated() {
        // AC (b): BEING_MITIGATED→MITIGATED
        RiskItem item = buildItem(RiskStatus.BEING_MITIGATED, RiskLevel.HIGH);
        when(riskItemRepository.findById(item.getId())).thenReturn(Optional.of(item));
        when(riskItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var response = useCase.execute(item.getId(),
            new UpdateRiskStatusRequest(RiskStatus.MITIGATED), "supervisor");

        assertThat(response.status()).isEqualTo(RiskStatus.MITIGATED);
    }

    @Test
    void shouldTransitionFromMitigated_to_Accepted_whenRiskLevelIsLow() {
        // AC (c): MITIGATED→ACCEPTED (riskLevel=LOW): ok (não bloqueia)
        RiskItem item = buildItem(RiskStatus.MITIGATED, RiskLevel.LOW);
        when(riskItemRepository.findById(item.getId())).thenReturn(Optional.of(item));
        when(riskItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var response = useCase.execute(item.getId(),
            new UpdateRiskStatusRequest(RiskStatus.ACCEPTED), "supervisor");

        assertThat(response.status()).isEqualTo(RiskStatus.ACCEPTED);
    }

    @Test
    void shouldThrow_whenCriticalRisk_acceptedWithoutCompletedMitigationBelow100() {
        // AC (d): CRITICAL→ACCEPTED sem mitigação COMPLETED com residualRpn≤100 → exception
        RiskItem item = buildItem(RiskStatus.MITIGATED, RiskLevel.CRITICAL);
        when(riskItemRepository.findById(item.getId())).thenReturn(Optional.of(item));
        when(mitigationRepository.existsByRiskItemIdAndStatusAndResidualRpnLessThanEqual(
            item.getId(), MitigationStatus.COMPLETED, 100)).thenReturn(false);

        assertThatThrownBy(() -> useCase.execute(item.getId(),
            new UpdateRiskStatusRequest(RiskStatus.ACCEPTED), "supervisor"))
            .isInstanceOf(InvalidRiskStatusTransitionException.class)
            .hasMessageContaining("Riscos críticos devem ser mitigados antes de aceitar");
    }

    @Test
    void shouldAcceptCriticalRisk_whenCompletedMitigationWithResidualRpn80Exists() {
        // AC (e): CRITICAL→ACCEPTED com mitigação COMPLETED e residualRpn=80: aceito
        RiskItem item = buildItem(RiskStatus.MITIGATED, RiskLevel.CRITICAL);
        when(riskItemRepository.findById(item.getId())).thenReturn(Optional.of(item));
        when(mitigationRepository.existsByRiskItemIdAndStatusAndResidualRpnLessThanEqual(
            item.getId(), MitigationStatus.COMPLETED, 100)).thenReturn(true);
        when(riskItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var response = useCase.execute(item.getId(),
            new UpdateRiskStatusRequest(RiskStatus.ACCEPTED), "supervisor");

        assertThat(response.status()).isEqualTo(RiskStatus.ACCEPTED);
    }

    @Test
    void shouldThrow_whenInvalidTransition_acceptedToIdentified() {
        // AC (f): Transição inválida (ACCEPTED→IDENTIFIED) → exception
        RiskItem item = buildItem(RiskStatus.ACCEPTED, RiskLevel.LOW);
        when(riskItemRepository.findById(item.getId())).thenReturn(Optional.of(item));

        assertThatThrownBy(() -> useCase.execute(item.getId(),
            new UpdateRiskStatusRequest(RiskStatus.IDENTIFIED), "supervisor"))
            .isInstanceOf(InvalidRiskStatusTransitionException.class);
    }
}
