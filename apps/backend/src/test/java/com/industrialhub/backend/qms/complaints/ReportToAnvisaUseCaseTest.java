package com.industrialhub.backend.qms.complaints;

import com.industrialhub.backend.common.application.AuditService;
import com.industrialhub.backend.qms.complaints.application.dto.AnvisaReportRequest;
import com.industrialhub.backend.qms.complaints.application.usecase.ReportToAnvisaUseCase;
import com.industrialhub.backend.qms.complaints.domain.ComplaintSource;
import com.industrialhub.backend.qms.complaints.domain.ComplaintStatus;
import com.industrialhub.backend.qms.complaints.domain.CustomerComplaint;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportToAnvisaUseCaseTest {

    @Mock private CustomerComplaintRepository complaintRepository;
    @Mock private AuditService auditService;

    private ReportToAnvisaUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ReportToAnvisaUseCase(complaintRepository, auditService);
    }

    private CustomerComplaint buildComplaint(ComplaintStatus status) {
        return CustomerComplaint.builder()
            .id(UUID.randomUUID())
            .code("REC-2026-001")
            .title("Reclamação grave")
            .description("Evento adverso")
            .source(ComplaintSource.REGULATORY_BODY)
            .severity(NcSeverity.CRITICAL)
            .status(status)
            .reportedDate(LocalDate.now())
            .reportedBy("ANVISA")
            .assignedTo("qualidade")
            .createdBy("admin")
            .createdAt(LocalDateTime.now())
            .build();
    }

    @Test
    void acD_dadosValidos_reportedToAnvisaTrue_anvisaReportDateENumberPreenchidos() {
        UUID id = UUID.randomUUID();
        CustomerComplaint complaint = buildComplaint(ComplaintStatus.CLOSED);
        complaint.setId(id);

        when(complaintRepository.findById(id)).thenReturn(Optional.of(complaint));
        when(complaintRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        LocalDate reportDate = LocalDate.of(2026, 6, 9);
        AnvisaReportRequest req = new AnvisaReportRequest("ANVISA-2026-001", reportDate);

        var result = useCase.execute(id, req, "admin");

        assertThat(result.reportedToAnvisa()).isTrue();
        assertThat(result.anvisaReportNumber()).isEqualTo("ANVISA-2026-001");
        assertThat(result.anvisaReportDate()).isEqualTo(reportDate);
    }

    @Test
    void acE_complaintClosed_reportedToAnvisaFalse_podeSerReportada() {
        UUID id = UUID.randomUUID();
        CustomerComplaint complaint = buildComplaint(ComplaintStatus.CLOSED);
        complaint.setId(id);
        assertThat(complaint.isReportedToAnvisa()).isFalse();

        when(complaintRepository.findById(id)).thenReturn(Optional.of(complaint));
        when(complaintRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AnvisaReportRequest req = new AnvisaReportRequest("ANVISA-2026-002", LocalDate.now());
        var result = useCase.execute(id, req, "admin");

        assertThat(result.reportedToAnvisa()).isTrue();
    }
}
