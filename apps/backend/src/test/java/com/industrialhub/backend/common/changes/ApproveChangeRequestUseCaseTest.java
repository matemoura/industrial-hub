package com.industrialhub.backend.common.changes;

import com.industrialhub.backend.common.application.AuditService;
import com.industrialhub.backend.common.changes.application.dto.ApproveChangeRequestRequest;
import com.industrialhub.backend.common.changes.application.usecase.ApproveChangeRequestUseCase;
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
class ApproveChangeRequestUseCaseTest {

    @Mock private ChangeRequestRepository changeRequestRepository;
    @Mock private AuditService auditService;

    private ApproveChangeRequestUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ApproveChangeRequestUseCase(changeRequestRepository, auditService);
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
    void acD_adminApproved_true_statusViraAPPROVED_comApprovedByEApprovedAtPreenchidos() {
        // AC (d): ADMIN, approved=true → APPROVED; approvedBy e approvedAt preenchidos
        ChangeRequest cr = buildCr(ChangeStatus.UNDER_REVIEW);
        when(changeRequestRepository.findById(cr.getId())).thenReturn(Optional.of(cr));
        when(changeRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var response = useCase.execute(cr.getId(), new ApproveChangeRequestRequest(true, null), "admin1");

        assertThat(response.status()).isEqualTo(ChangeStatus.APPROVED);
        assertThat(response.approvedBy()).isEqualTo("admin1");
        assertThat(response.approvedAt()).isNotNull();
    }

    @Test
    void acE_adminApproved_false_comRejectionReason_statusViraREJECTED() {
        // AC (e): ADMIN, approved=false + rejectionReason → REJECTED
        ChangeRequest cr = buildCr(ChangeStatus.UNDER_REVIEW);
        when(changeRequestRepository.findById(cr.getId())).thenReturn(Optional.of(cr));
        when(changeRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var response = useCase.execute(cr.getId(),
            new ApproveChangeRequestRequest(false, "Motivo da rejeição"), "admin1");

        assertThat(response.status()).isEqualTo(ChangeStatus.REJECTED);
        assertThat(response.rejectionReason()).isEqualTo("Motivo da rejeição");
    }

    @Test
    void acF_statusNaoUNDER_REVIEW_lancaInvalidChangeStatusTransitionException() {
        // AC (f): status != UNDER_REVIEW → InvalidChangeStatusTransitionException (422)
        ChangeRequest cr = buildCr(ChangeStatus.SUBMITTED);
        when(changeRequestRepository.findById(cr.getId())).thenReturn(Optional.of(cr));

        assertThatThrownBy(() -> useCase.execute(cr.getId(),
            new ApproveChangeRequestRequest(true, null), "admin1"))
            .isInstanceOf(InvalidChangeStatusTransitionException.class);
    }
}
