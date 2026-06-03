package com.industrialhub.backend.qms;

import com.industrialhub.backend.qms.application.dto.ActionResponse;
import com.industrialhub.backend.qms.application.usecase.SubmitForEffectivenessUseCase;
import com.industrialhub.backend.qms.domain.ActionNotAllowedException;
import com.industrialhub.backend.qms.domain.ActionStatus;
import com.industrialhub.backend.qms.domain.ActionType;
import com.industrialhub.backend.qms.domain.CorrectiveAction;
import com.industrialhub.backend.qms.domain.NonConformance;
import com.industrialhub.backend.qms.infrastructure.CorrectiveActionRepository;
import com.industrialhub.backend.qms.infrastructure.NonConformanceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubmitForEffectivenessUseCaseTest {

    @Mock
    private NonConformanceRepository ncRepository;

    @Mock
    private CorrectiveActionRepository actionRepository;

    private SubmitForEffectivenessUseCase useCase;

    private UUID ncId;
    private UUID actionId;
    private NonConformance nc;

    @BeforeEach
    void setUp() {
        useCase = new SubmitForEffectivenessUseCase(ncRepository, actionRepository);
        ncId = UUID.randomUUID();
        actionId = UUID.randomUUID();
        nc = NonConformance.builder()
                .id(ncId)
                .title("NC Test")
                .build();
    }

    @Test
    void submitForEffectiveness_success() {
        CorrectiveAction action = CorrectiveAction.builder()
                .id(actionId)
                .nonConformance(nc)
                .description("Fix issue")
                .responsible("user")
                .dueDate(LocalDate.now().plusDays(7))
                .status(ActionStatus.PENDING)
                .type(ActionType.CORRECTIVE)
                .rootCauseConfirmed("Root cause identified")
                .build();

        when(ncRepository.existsById(ncId)).thenReturn(true);
        when(actionRepository.findById(actionId)).thenReturn(Optional.of(action));

        ActionResponse response = useCase.execute(ncId, actionId, "supervisor");

        assertThat(response.status()).isEqualTo(ActionStatus.PENDING_EFFECTIVENESS);
    }

    @Test
    void submitForEffectiveness_noRootCause_throwsException() {
        CorrectiveAction action = CorrectiveAction.builder()
                .id(actionId)
                .nonConformance(nc)
                .description("Fix issue")
                .responsible("user")
                .dueDate(LocalDate.now().plusDays(7))
                .status(ActionStatus.PENDING)
                .type(ActionType.CORRECTIVE)
                .rootCauseConfirmed(null)
                .build();

        when(ncRepository.existsById(ncId)).thenReturn(true);
        when(actionRepository.findById(actionId)).thenReturn(Optional.of(action));

        assertThatThrownBy(() -> useCase.execute(ncId, actionId, "supervisor"))
                .isInstanceOf(ActionNotAllowedException.class)
                .hasMessageContaining("Causa raiz confirmada");
    }
}
