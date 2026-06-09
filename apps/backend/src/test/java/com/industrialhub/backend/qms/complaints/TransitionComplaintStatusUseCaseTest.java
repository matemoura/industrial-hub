package com.industrialhub.backend.qms.complaints;

import com.industrialhub.backend.common.application.AuditService;
import com.industrialhub.backend.qms.complaints.application.dto.UpdateComplaintStatusRequest;
import com.industrialhub.backend.qms.complaints.application.usecase.TransitionComplaintStatusUseCase;
import com.industrialhub.backend.qms.complaints.application.usecase.UpdateComplaintUseCase;
import com.industrialhub.backend.qms.complaints.application.dto.UpdateComplaintRequest;
import com.industrialhub.backend.qms.complaints.domain.ComplaintClosedException;
import com.industrialhub.backend.qms.complaints.domain.ComplaintSource;
import com.industrialhub.backend.qms.complaints.domain.ComplaintStatus;
import com.industrialhub.backend.qms.complaints.domain.CustomerComplaint;
import com.industrialhub.backend.qms.complaints.domain.InvalidComplaintStatusTransitionException;
import com.industrialhub.backend.qms.complaints.infrastructure.CustomerComplaintRepository;
import com.industrialhub.backend.qms.domain.NcSeverity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransitionComplaintStatusUseCaseTest {

    @Mock private CustomerComplaintRepository complaintRepository;
    @Mock private AuditService auditService;

    private TransitionComplaintStatusUseCase transitionUseCase;
    private UpdateComplaintUseCase updateUseCase;

    @BeforeEach
    void setUp() {
        transitionUseCase = new TransitionComplaintStatusUseCase(complaintRepository, auditService);
        updateUseCase = new UpdateComplaintUseCase(complaintRepository);
    }

    private CustomerComplaint buildComplaint(ComplaintStatus status, String investigationSummary, String rootCause) {
        return CustomerComplaint.builder()
            .id(UUID.randomUUID())
            .code("REC-2026-001")
            .title("Reclamação teste")
            .description("Descrição")
            .source(ComplaintSource.CLIENT)
            .severity(NcSeverity.HIGH)
            .status(status)
            .reportedDate(LocalDate.now())
            .reportedBy("Cliente")
            .assignedTo("qualidade")
            .investigationSummary(investigationSummary)
            .rootCause(rootCause)
            .createdBy("supervisor")
            .createdAt(LocalDateTime.now())
            .build();
    }

    @Test
    void acA_investigationCompletedParaClosed_comDados_statusClosedEClosedAtPreenchido() {
        UUID id = UUID.randomUUID();
        CustomerComplaint complaint = buildComplaint(
            ComplaintStatus.INVESTIGATION_COMPLETED,
            "Produto com defeito de fabricação",
            "Falha no processo de inspeção"
        );
        complaint.setId(id);

        when(complaintRepository.findById(id)).thenReturn(Optional.of(complaint));
        when(complaintRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var result = transitionUseCase.execute(id,
            new UpdateComplaintStatusRequest(ComplaintStatus.CLOSED, null), "admin");

        assertThat(result.status()).isEqualTo(ComplaintStatus.CLOSED);
        assertThat(complaint.getClosedAt()).isNotNull();
    }

    @Test
    void acB_investigationCompletedParaClosed_semInvestigationSummary_lancaException() {
        UUID id = UUID.randomUUID();
        CustomerComplaint complaint = buildComplaint(
            ComplaintStatus.INVESTIGATION_COMPLETED,
            null,
            "Causa raiz identificada"
        );
        complaint.setId(id);

        when(complaintRepository.findById(id)).thenReturn(Optional.of(complaint));

        assertThatThrownBy(() -> transitionUseCase.execute(id,
            new UpdateComplaintStatusRequest(ComplaintStatus.CLOSED, null), "admin"))
            .isInstanceOf(InvalidComplaintStatusTransitionException.class)
            .hasMessageContaining("investigationSummary");
    }

    @Test
    void acC_updateComplaint_quandoStatusClosed_lancaComplaintClosedException() {
        UUID id = UUID.randomUUID();
        CustomerComplaint complaint = buildComplaint(ComplaintStatus.CLOSED, "summary", "rootCause");
        complaint.setId(id);

        when(complaintRepository.findById(id)).thenReturn(Optional.of(complaint));

        assertThatThrownBy(() -> updateUseCase.execute(id,
            new UpdateComplaintRequest("Novo título", null, null, null, null, null, null, null)))
            .isInstanceOf(ComplaintClosedException.class);
    }
}
