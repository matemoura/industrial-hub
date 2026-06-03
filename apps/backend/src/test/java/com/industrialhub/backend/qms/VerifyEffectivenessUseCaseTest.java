package com.industrialhub.backend.qms;

import com.industrialhub.backend.qms.application.dto.ActionResponse;
import com.industrialhub.backend.qms.application.dto.VerifyEffectivenessRequest;
import com.industrialhub.backend.qms.application.usecase.VerifyEffectivenessUseCase;
import com.industrialhub.backend.qms.domain.ActionNotAllowedException;
import com.industrialhub.backend.qms.domain.ActionStatus;
import com.industrialhub.backend.qms.domain.ActionType;
import com.industrialhub.backend.qms.domain.CorrectiveAction;
import com.industrialhub.backend.qms.domain.NcStatus;
import com.industrialhub.backend.qms.domain.NonConformance;
import com.industrialhub.backend.qms.infrastructure.CorrectiveActionRepository;
import com.industrialhub.backend.qms.infrastructure.NonConformanceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VerifyEffectivenessUseCaseTest {

    @Mock
    private NonConformanceRepository ncRepository;

    @Mock
    private CorrectiveActionRepository actionRepository;

    private VerifyEffectivenessUseCase useCase;

    private UUID ncId;
    private UUID actionId;

    @BeforeEach
    void setUp() {
        useCase = new VerifyEffectivenessUseCase(ncRepository, actionRepository);
        ncId = UUID.randomUUID();
        actionId = UUID.randomUUID();
    }

    @Test
    void verifyEffectiveness_lastAction_closesNc() {
        NonConformance nc = NonConformance.builder()
                .id(ncId)
                .title("NC Test")
                .status(NcStatus.IN_ANALYSIS)
                .build();

        CorrectiveAction action = CorrectiveAction.builder()
                .id(actionId)
                .nonConformance(nc)
                .description("Fix issue")
                .responsible("user")
                .dueDate(LocalDate.now().plusDays(7))
                .status(ActionStatus.PENDING_EFFECTIVENESS)
                .type(ActionType.CORRECTIVE)
                .build();

        VerifyEffectivenessRequest req = new VerifyEffectivenessRequest(
                "Eficácia confirmada", "supervisor");

        when(ncRepository.findById(ncId)).thenReturn(Optional.of(nc));
        // SEC-139: use findByIdForUpdate (PESSIMISTIC_WRITE lock)
        when(actionRepository.findByIdForUpdate(actionId)).thenReturn(Optional.of(action));
        when(actionRepository.existsByNonConformanceIdAndStatusIn(
                eq(ncId), any(List.class))).thenReturn(false);

        ActionResponse response = useCase.execute(ncId, actionId, req, "supervisor");

        assertThat(response.status()).isEqualTo(ActionStatus.DONE);

        ArgumentCaptor<NonConformance> ncCaptor = ArgumentCaptor.forClass(NonConformance.class);
        verify(ncRepository).save(ncCaptor.capture());
        assertThat(ncCaptor.getValue().getStatus()).isEqualTo(NcStatus.CLOSED);
        assertThat(ncCaptor.getValue().getClosedBy()).isEqualTo("supervisor");
        assertThat(ncCaptor.getValue().getClosedAt()).isNotNull();
    }

    @Test
    void verifyEffectiveness_notLastAction_ncStaysOpen() {
        NonConformance nc = NonConformance.builder()
                .id(ncId)
                .title("NC Test")
                .status(NcStatus.IN_ANALYSIS)
                .build();

        CorrectiveAction action = CorrectiveAction.builder()
                .id(actionId)
                .nonConformance(nc)
                .description("Fix issue")
                .responsible("user")
                .dueDate(LocalDate.now().plusDays(7))
                .status(ActionStatus.PENDING_EFFECTIVENESS)
                .type(ActionType.CORRECTIVE)
                .build();

        VerifyEffectivenessRequest req = new VerifyEffectivenessRequest(
                "Eficácia confirmada", "supervisor");

        when(ncRepository.findById(ncId)).thenReturn(Optional.of(nc));
        // SEC-139: use findByIdForUpdate (PESSIMISTIC_WRITE lock)
        when(actionRepository.findByIdForUpdate(actionId)).thenReturn(Optional.of(action));
        when(actionRepository.existsByNonConformanceIdAndStatusIn(
                eq(ncId), any(List.class))).thenReturn(true);

        ActionResponse response = useCase.execute(ncId, actionId, req, "supervisor");

        assertThat(response.status()).isEqualTo(ActionStatus.DONE);
        assertThat(nc.getStatus()).isEqualTo(NcStatus.IN_ANALYSIS);
    }

    // --- SEC-139: verify use case calls findByIdForUpdate, not findById ---

    @Test
    void verifyEffectiveness_usesLockedRead_notSimpleFindById() {
        NonConformance nc = NonConformance.builder()
                .id(ncId).title("NC Test").status(NcStatus.IN_ANALYSIS).build();

        CorrectiveAction action = CorrectiveAction.builder()
                .id(actionId).nonConformance(nc).description("Fix")
                .responsible("user").dueDate(LocalDate.now().plusDays(7))
                .status(ActionStatus.PENDING_EFFECTIVENESS).type(ActionType.CORRECTIVE)
                .build();

        VerifyEffectivenessRequest req = new VerifyEffectivenessRequest("OK", "supervisor");

        when(ncRepository.findById(ncId)).thenReturn(Optional.of(nc));
        when(actionRepository.findByIdForUpdate(actionId)).thenReturn(Optional.of(action));
        when(actionRepository.existsByNonConformanceIdAndStatusIn(eq(ncId), any())).thenReturn(true);

        useCase.execute(ncId, actionId, req, "supervisor");

        // Must use locked read
        verify(actionRepository).findByIdForUpdate(actionId);
    }
}
