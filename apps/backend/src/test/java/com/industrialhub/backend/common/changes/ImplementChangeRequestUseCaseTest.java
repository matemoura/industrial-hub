package com.industrialhub.backend.common.changes;

import com.industrialhub.backend.common.application.AuditService;
import com.industrialhub.backend.common.changes.application.usecase.ImplementChangeRequestUseCase;
import com.industrialhub.backend.common.changes.domain.ChangeRequest;
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
class ImplementChangeRequestUseCaseTest {

    @Mock private ChangeRequestRepository changeRequestRepository;
    @Mock private AuditService auditService;

    private ImplementChangeRequestUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ImplementChangeRequestUseCase(changeRequestRepository, auditService);
    }

    private ChangeRequest buildCr(ChangeStatus status) {
        ChangeRequest cr = new ChangeRequest();
        cr.setId(UUID.randomUUID());
        cr.setCode("CR-2026-001");
        cr.setTitle("Mudança de processo");
        cr.setDescription("Descrição");
        cr.setChangeType(ChangeType.PROCESS);
        cr.setJustification("Justificativa");
        cr.setStatus(status);
        cr.setRequestedBy("user1");
        cr.setCreatedAt(LocalDateTime.now());
        return cr;
    }

    @Test
    void acG_approvedViraIMPLEMENTED_comImplementedAtPreenchido() {
        // AC (g): APPROVED → IMPLEMENTED; implementedAt preenchido
        ChangeRequest cr = buildCr(ChangeStatus.APPROVED);
        when(changeRequestRepository.findById(cr.getId())).thenReturn(Optional.of(cr));
        when(changeRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var response = useCase.execute(cr.getId(), "supervisor1");

        assertThat(response.status()).isEqualTo(ChangeStatus.IMPLEMENTED);
        assertThat(response.implementedAt()).isNotNull();
    }

    @Test
    void acH_rejectedNaoPodesImplementar_lancaInvalidChangeStatusTransitionException() {
        // AC (h): REJECTED → InvalidChangeStatusTransitionException (422)
        ChangeRequest cr = buildCr(ChangeStatus.REJECTED);
        when(changeRequestRepository.findById(cr.getId())).thenReturn(Optional.of(cr));

        assertThatThrownBy(() -> useCase.execute(cr.getId(), "supervisor1"))
            .isInstanceOf(InvalidChangeStatusTransitionException.class);
    }
}
