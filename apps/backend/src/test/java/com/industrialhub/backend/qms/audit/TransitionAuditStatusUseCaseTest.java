package com.industrialhub.backend.qms.audit;

import com.industrialhub.backend.common.application.AuditService;
import com.industrialhub.backend.qms.audit.application.dto.InternalAuditResponse;
import com.industrialhub.backend.qms.audit.application.dto.UpdateAuditStatusRequest;
import com.industrialhub.backend.qms.audit.application.usecase.TransitionAuditStatusUseCase;
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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransitionAuditStatusUseCaseTest {

    @Mock private InternalAuditRepository auditRepository;
    @Mock private AuditChecklistItemRepository checklistRepository;
    @Mock private AuditFindingRepository findingRepository;
    @Mock private AuditService auditService;

    private TransitionAuditStatusUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new TransitionAuditStatusUseCase(
            auditRepository, checklistRepository, findingRepository, auditService);
    }

    private InternalAudit buildAudit(AuditStatus status) {
        return InternalAudit.builder()
            .id(UUID.randomUUID())
            .code("AUD-2026-001")
            .title("Auditoria Interna QMS")
            .scope("Processos de qualidade")
            .auditType(AuditType.INTERNAL)
            .status(status)
            .plannedDate(LocalDate.now().plusDays(7))
            .leadAuditor("auditor1")
            .createdBy("supervisor1")
            .createdAt(LocalDateTime.now())
            .build();
    }

    @Test
    void shouldTransition_fromPlannedToInProgress() {
        // AC (a) US-124: PLANNED → IN_PROGRESS ok
        InternalAudit audit = buildAudit(AuditStatus.PLANNED);
        when(auditRepository.findById(audit.getId())).thenReturn(Optional.of(audit));
        when(auditRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        InternalAuditResponse response = useCase.execute(
            audit.getId(), new UpdateAuditStatusRequest(AuditStatus.IN_PROGRESS, null), "supervisor1");

        assertThat(response.status()).isEqualTo(AuditStatus.IN_PROGRESS);
    }

    @Test
    void shouldThrow_whenTransitionInProgressToCompleted_withoutCompletedDate() {
        // AC (b) US-124: IN_PROGRESS → COMPLETED sem completedDate → 422
        InternalAudit audit = buildAudit(AuditStatus.IN_PROGRESS);
        when(auditRepository.findById(audit.getId())).thenReturn(Optional.of(audit));

        assertThatThrownBy(() -> useCase.execute(
            audit.getId(), new UpdateAuditStatusRequest(AuditStatus.COMPLETED, null), "supervisor1"))
            .isInstanceOf(InvalidAuditStatusTransitionException.class)
            .hasMessageContaining("completedDate");
    }

    @Test
    void shouldThrow_whenTransitionInProgressToCancelled() {
        // AC (c) US-124: IN_PROGRESS → CANCELLED → 422
        InternalAudit audit = buildAudit(AuditStatus.IN_PROGRESS);
        when(auditRepository.findById(audit.getId())).thenReturn(Optional.of(audit));

        assertThatThrownBy(() -> useCase.execute(
            audit.getId(), new UpdateAuditStatusRequest(AuditStatus.CANCELLED, null), "supervisor1"))
            .isInstanceOf(InvalidAuditStatusTransitionException.class);
    }

    @Test
    void shouldTransition_fromPlannedToCancelled() {
        // AC (d) US-124: PLANNED → CANCELLED ok
        InternalAudit audit = buildAudit(AuditStatus.PLANNED);
        when(auditRepository.findById(audit.getId())).thenReturn(Optional.of(audit));
        when(auditRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        InternalAuditResponse response = useCase.execute(
            audit.getId(), new UpdateAuditStatusRequest(AuditStatus.CANCELLED, null), "supervisor1");

        assertThat(response.status()).isEqualTo(AuditStatus.CANCELLED);
    }
}
