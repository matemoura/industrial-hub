package com.industrialhub.backend.qms.complaints;

import com.industrialhub.backend.common.application.AuditService;
import com.industrialhub.backend.qms.complaints.application.usecase.GenerateMdrReportUseCase;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GenerateMdrReportUseCaseTest {

    @Mock private CustomerComplaintRepository complaintRepository;
    @Mock private AuditService auditService;

    private GenerateMdrReportUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GenerateMdrReportUseCase(complaintRepository, auditService);
    }

    private CustomerComplaint buildEligibleComplaint() {
        return CustomerComplaint.builder()
            .id(UUID.randomUUID())
            .code("REC-2026-001")
            .title("Evento adverso grave")
            .description("Produto causou reação adversa em paciente")
            .source(ComplaintSource.CLIENT)
            .productCode("PROD-STENT-002")
            .batchNumber("LOTE-2026-06")
            .severity(NcSeverity.CRITICAL)
            .status(ComplaintStatus.CLOSED)
            .reportedDate(LocalDate.of(2026, 1, 15))
            .reportedBy("Hospital São Lucas")
            .assignedTo("qualidade")
            .investigationSummary("Investigação completa: falha na esterilização do lote")
            .rootCause("Temperatura incorreta no ciclo de esterilização")
            .correctiveAction("Revisão completa do processo de esterilização e retirada do lote")
            .reportedToAnvisa(true)
            .anvisaReportNumber("ANVISA-MDR-2026-001")
            .anvisaReportDate(LocalDate.of(2026, 2, 1))
            .createdBy("supervisor")
            .createdAt(LocalDateTime.now())
            .closedAt(LocalDateTime.now())
            .build();
    }

    @Test
    void acA_elegivel_bytesNaoVaziosEMagicBytesPDF() {
        UUID id = UUID.randomUUID();
        CustomerComplaint complaint = buildEligibleComplaint();
        complaint.setId(id);

        when(complaintRepository.findById(id)).thenReturn(Optional.of(complaint));

        byte[] pdf = useCase.execute(id, "admin");

        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
    }

    @Test
    void acB_reportedToAnvisaFalse_lanca422InvalidComplaintStatusTransitionException() {
        UUID id = UUID.randomUUID();
        CustomerComplaint complaint = buildEligibleComplaint();
        complaint.setId(id);
        complaint.setReportedToAnvisa(false);

        when(complaintRepository.findById(id)).thenReturn(Optional.of(complaint));

        assertThatThrownBy(() -> useCase.execute(id, "admin"))
            .isInstanceOf(InvalidComplaintStatusTransitionException.class)
            .hasMessageContaining("ANVISA");
    }

    @Test
    void acC_statusNaoClosed_lanca422InvalidComplaintStatusTransitionException() {
        UUID id = UUID.randomUUID();
        CustomerComplaint complaint = buildEligibleComplaint();
        complaint.setId(id);
        complaint.setStatus(ComplaintStatus.UNDER_INVESTIGATION);

        when(complaintRepository.findById(id)).thenReturn(Optional.of(complaint));

        assertThatThrownBy(() -> useCase.execute(id, "admin"))
            .isInstanceOf(InvalidComplaintStatusTransitionException.class)
            .hasMessageContaining("CLOSED");
    }
}
