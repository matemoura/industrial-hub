package com.industrialhub.backend.qms.audit;

import com.industrialhub.backend.common.application.AuditService;
import com.industrialhub.backend.qms.audit.application.usecase.GenerateAuditReportUseCase;
import com.industrialhub.backend.qms.audit.domain.AuditStatus;
import com.industrialhub.backend.qms.audit.domain.AuditType;
import com.industrialhub.backend.qms.audit.domain.InternalAudit;
import com.industrialhub.backend.qms.audit.domain.InvalidAuditStatusTransitionException;
import com.industrialhub.backend.qms.audit.infrastructure.AuditChecklistItemRepository;
import com.industrialhub.backend.qms.audit.infrastructure.AuditFindingRepository;
import com.industrialhub.backend.qms.audit.infrastructure.InternalAuditRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GenerateAuditReportUseCaseTest {

    @Mock private InternalAuditRepository auditRepository;
    @Mock private AuditChecklistItemRepository checklistRepository;
    @Mock private AuditFindingRepository findingRepository;
    @Mock private AuditService auditService;

    private GenerateAuditReportUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GenerateAuditReportUseCase(
            auditRepository, checklistRepository, findingRepository, auditService);
    }

    private InternalAudit buildAudit(AuditStatus status) {
        return InternalAudit.builder()
            .id(UUID.randomUUID())
            .code("AUD-2026-001")
            .title("Auditoria Interna QMS")
            .scope("Todos os processos do SGQ")
            .auditType(AuditType.INTERNAL)
            .status(status)
            .plannedDate(LocalDate.now().minusDays(30))
            .completedDate(status == AuditStatus.COMPLETED ? LocalDate.now() : null)
            .leadAuditor("auditor1")
            .auditees(Set.of("qualidade"))
            .createdBy("supervisor1")
            .createdAt(LocalDateTime.now().minusDays(30))
            .build();
    }

    @Test
    void shouldReturnNonEmptyPdf_withMagicBytes_forCompletedAudit() {
        // AC US-125 (a): COMPLETED → bytes não-vazios, começa com %PDF
        InternalAudit audit = buildAudit(AuditStatus.COMPLETED);
        when(auditRepository.findById(audit.getId())).thenReturn(Optional.of(audit));
        when(checklistRepository.findByAuditIdOrderByItemOrder(audit.getId())).thenReturn(List.of());
        when(findingRepository.findByAuditId(audit.getId())).thenReturn(List.of());

        byte[] pdf = useCase.execute(audit.getId(), "supervisor1");

        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
    }

    @Test
    void shouldThrow_whenStatusIsPlanned() {
        // AC US-125 (b): PLANNED → 422
        InternalAudit audit = buildAudit(AuditStatus.PLANNED);
        when(auditRepository.findById(audit.getId())).thenReturn(Optional.of(audit));

        assertThatThrownBy(() -> useCase.execute(audit.getId(), "supervisor1"))
            .isInstanceOf(InvalidAuditStatusTransitionException.class)
            .hasMessageContaining("COMPLETED");
    }

    @Test
    void shouldThrow_whenStatusIsCancelled() {
        // AC US-125 (c): CANCELLED → 422
        InternalAudit audit = buildAudit(AuditStatus.CANCELLED);
        when(auditRepository.findById(audit.getId())).thenReturn(Optional.of(audit));

        assertThatThrownBy(() -> useCase.execute(audit.getId(), "supervisor1"))
            .isInstanceOf(InvalidAuditStatusTransitionException.class);
    }
}
