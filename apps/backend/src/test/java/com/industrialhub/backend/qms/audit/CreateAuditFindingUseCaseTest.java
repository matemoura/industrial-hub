package com.industrialhub.backend.qms.audit;

import com.industrialhub.backend.common.application.AuditService;
import com.industrialhub.backend.qms.audit.application.dto.AuditFindingResponse;
import com.industrialhub.backend.qms.audit.application.dto.CreateAuditFindingRequest;
import com.industrialhub.backend.qms.audit.application.usecase.CreateAuditFindingUseCase;
import com.industrialhub.backend.qms.audit.domain.AuditFinding;
import com.industrialhub.backend.qms.audit.domain.AuditStatus;
import com.industrialhub.backend.qms.audit.domain.AuditType;
import com.industrialhub.backend.qms.audit.domain.FindingType;
import com.industrialhub.backend.qms.audit.domain.InternalAudit;
import com.industrialhub.backend.qms.audit.domain.InvalidAuditStatusTransitionException;
import com.industrialhub.backend.qms.audit.infrastructure.AuditChecklistItemRepository;
import com.industrialhub.backend.qms.audit.infrastructure.AuditFindingRepository;
import com.industrialhub.backend.qms.audit.infrastructure.InternalAuditRepository;
import com.industrialhub.backend.qms.domain.NcNotFoundException;
import com.industrialhub.backend.qms.domain.NcSeverity;
import com.industrialhub.backend.qms.infrastructure.NonConformanceRepository;
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
class CreateAuditFindingUseCaseTest {

    @Mock private InternalAuditRepository auditRepository;
    @Mock private AuditFindingRepository findingRepository;
    @Mock private AuditChecklistItemRepository checklistRepository;
    @Mock private NonConformanceRepository ncRepository;
    @Mock private AuditService auditService;

    private CreateAuditFindingUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new CreateAuditFindingUseCase(
            auditRepository, findingRepository, checklistRepository, ncRepository, auditService);
    }

    private InternalAudit buildAudit(AuditStatus status) {
        return InternalAudit.builder()
            .id(UUID.randomUUID())
            .code("AUD-2026-001")
            .title("Auditoria QMS")
            .scope("SGQ")
            .auditType(AuditType.INTERNAL)
            .status(status)
            .plannedDate(LocalDate.now())
            .leadAuditor("auditor1")
            .createdBy("supervisor1")
            .createdAt(LocalDateTime.now())
            .build();
    }

    @Test
    void shouldPersistFinding_withLinkedNcId_whenNcExists() {
        // AC (e) US-124: linkedNcId válido → persiste achado com linkedNcId
        UUID ncId = UUID.randomUUID();
        InternalAudit audit = buildAudit(AuditStatus.IN_PROGRESS);

        when(auditRepository.findById(audit.getId())).thenReturn(Optional.of(audit));
        when(ncRepository.existsById(ncId)).thenReturn(true);
        when(findingRepository.save(any())).thenAnswer(inv -> {
            AuditFinding f = inv.getArgument(0);
            f.setId(UUID.randomUUID());
            f.setCreatedAt(LocalDateTime.now());
            return f;
        });

        var req = new CreateAuditFindingRequest(
            FindingType.NON_CONFORMANCE, "Desvio encontrado", "8.2.4",
            NcSeverity.HIGH, null, ncId, null);

        AuditFindingResponse response = useCase.execute(audit.getId(), req, "supervisor1");

        assertThat(response.linkedNcId()).isEqualTo(ncId);
        assertThat(response.type()).isEqualTo(FindingType.NON_CONFORMANCE);
    }

    @Test
    void shouldThrow_whenLinkedNcIdDoesNotExist() {
        // AC (f) US-124: linkedNcId inexistente → NcNotFoundException → 404
        UUID unknownNcId = UUID.randomUUID();
        InternalAudit audit = buildAudit(AuditStatus.IN_PROGRESS);

        when(auditRepository.findById(audit.getId())).thenReturn(Optional.of(audit));
        when(ncRepository.existsById(unknownNcId)).thenReturn(false);

        var req = new CreateAuditFindingRequest(
            FindingType.NON_CONFORMANCE, "Desvio", "8.2.4",
            NcSeverity.MEDIUM, null, unknownNcId, null);

        assertThatThrownBy(() -> useCase.execute(audit.getId(), req, "supervisor1"))
            .isInstanceOf(NcNotFoundException.class);
    }

    @Test
    void shouldThrow_whenAuditIsCompleted() {
        // AC (g) US-124: auditoria COMPLETED → InvalidAuditStatusTransitionException → 422
        InternalAudit audit = buildAudit(AuditStatus.COMPLETED);
        when(auditRepository.findById(audit.getId())).thenReturn(Optional.of(audit));

        var req = new CreateAuditFindingRequest(
            FindingType.OBSERVATION, "Observação", "8.2.4",
            NcSeverity.LOW, null, null, null);

        assertThatThrownBy(() -> useCase.execute(audit.getId(), req, "supervisor1"))
            .isInstanceOf(InvalidAuditStatusTransitionException.class);
    }
}
