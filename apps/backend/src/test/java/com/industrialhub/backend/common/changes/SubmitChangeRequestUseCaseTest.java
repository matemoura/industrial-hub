package com.industrialhub.backend.common.changes;

import com.industrialhub.backend.common.application.AuditService;
import com.industrialhub.backend.common.changes.application.usecase.SubmitChangeRequestUseCase;
import com.industrialhub.backend.common.changes.domain.ChangeRequest;
import com.industrialhub.backend.common.changes.domain.ChangeRequestForbiddenException;
import com.industrialhub.backend.common.changes.domain.ChangeStatus;
import com.industrialhub.backend.common.changes.domain.ChangeType;
import com.industrialhub.backend.common.changes.domain.InvalidChangeStatusTransitionException;
import com.industrialhub.backend.common.changes.infrastructure.ChangeRequestRepository;
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
class SubmitChangeRequestUseCaseTest {

    @Mock private ChangeRequestRepository changeRequestRepository;
    @Mock private AuditService auditService;

    private SubmitChangeRequestUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new SubmitChangeRequestUseCase(changeRequestRepository, auditService);
    }

    private ChangeRequest buildCr(ChangeStatus status, String requestedBy) {
        ChangeRequest cr = new ChangeRequest();
        cr.setId(UUID.randomUUID());
        cr.setCode("CR-2026-001");
        cr.setTitle("Mudança de processo");
        cr.setDescription("Descrição");
        cr.setChangeType(ChangeType.PROCESS);
        cr.setJustification("Justificativa");
        cr.setStatus(status);
        cr.setRequestedBy(requestedBy);
        cr.setCreatedAt(LocalDateTime.now());
        return cr;
    }

    @Test
    void acA_autorPodeSubmeter_statusViaSUBMITTED() {
        // AC (a): requestedBy == principal → SUBMITTED com submittedAt preenchido
        ChangeRequest cr = buildCr(ChangeStatus.DRAFT, "user1");
        when(changeRequestRepository.findById(cr.getId())).thenReturn(Optional.of(cr));
        when(changeRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var response = useCase.execute(cr.getId(), "user1");

        assertThat(response.status()).isEqualTo(ChangeStatus.SUBMITTED);
        assertThat(response.submittedAt()).isNotNull();
    }

    @Test
    void acB_outroUsuarioNaoPodeSubmeter_lancaChangeRequestForbiddenException() {
        // AC (b): requestedBy != principal → ChangeRequestForbiddenException (403)
        ChangeRequest cr = buildCr(ChangeStatus.DRAFT, "user1");
        when(changeRequestRepository.findById(cr.getId())).thenReturn(Optional.of(cr));

        assertThatThrownBy(() -> useCase.execute(cr.getId(), "outro_usuario"))
            .isInstanceOf(ChangeRequestForbiddenException.class);
    }

    @Test
    void acC_statusNaoDraft_lancaInvalidChangeStatusTransitionException() {
        // AC (c): status != DRAFT → InvalidChangeStatusTransitionException (422)
        ChangeRequest cr = buildCr(ChangeStatus.SUBMITTED, "user1");
        when(changeRequestRepository.findById(cr.getId())).thenReturn(Optional.of(cr));

        assertThatThrownBy(() -> useCase.execute(cr.getId(), "user1"))
            .isInstanceOf(InvalidChangeStatusTransitionException.class);
    }
}
