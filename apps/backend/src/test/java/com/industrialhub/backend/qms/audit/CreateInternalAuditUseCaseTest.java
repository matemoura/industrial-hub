package com.industrialhub.backend.qms.audit;

import com.industrialhub.backend.common.application.AuditService;
import com.industrialhub.backend.qms.audit.application.dto.CreateInternalAuditRequest;
import com.industrialhub.backend.qms.audit.application.dto.InternalAuditResponse;
import com.industrialhub.backend.qms.audit.application.usecase.CreateInternalAuditUseCase;
import com.industrialhub.backend.qms.audit.domain.AuditCodeAlreadyExistsException;
import com.industrialhub.backend.qms.audit.domain.AuditStatus;
import com.industrialhub.backend.qms.audit.domain.AuditType;
import com.industrialhub.backend.qms.audit.domain.InternalAudit;
import com.industrialhub.backend.qms.audit.infrastructure.InternalAuditRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreateInternalAuditUseCaseTest {

    @Mock private InternalAuditRepository auditRepository;
    @Mock private AuditService auditService;

    private CreateInternalAuditUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new CreateInternalAuditUseCase(auditRepository, auditService);
    }

    @Test
    void shouldGenerateCode_AUD_YEAR_001_forFirstAuditOfYear() {
        // AC (h) US-124: primeira auditoria do ano → AUD-{ANO}-001
        int year = LocalDate.now().getYear();
        String prefix = "AUD-" + year + "-";

        when(auditRepository.countByCodeStartingWith(startsWith("AUD-" + year))).thenReturn(0L);
        when(auditRepository.save(any())).thenAnswer(inv -> {
            InternalAudit a = inv.getArgument(0);
            a.setId(UUID.randomUUID());
            a.setCreatedAt(LocalDateTime.now());
            return a;
        });

        var req = new CreateInternalAuditRequest(
            "Auditoria Anual QMS", "Todos os processos do SGQ",
            AuditType.INTERNAL, LocalDate.now().plusDays(30),
            "auditor1", Set.of("qualidade"));

        InternalAuditResponse response = useCase.execute(req, "supervisor1");

        assertThat(response.code()).isEqualTo(prefix + "001");
        assertThat(response.status()).isEqualTo(AuditStatus.PLANNED);
    }

    @Test
    void shouldThrow_AuditCodeAlreadyExists_whenDataIntegrityViolation() {
        // AC (i) US-124: DataIntegrityViolationException → AuditCodeAlreadyExistsException → 409
        when(auditRepository.countByCodeStartingWith(any())).thenReturn(0L);
        when(auditRepository.save(any())).thenThrow(DataIntegrityViolationException.class);

        var req = new CreateInternalAuditRequest(
            "Auditoria QMS", "SGQ", AuditType.PROCESS,
            LocalDate.now().plusDays(14), "auditor2", Set.of());

        assertThatThrownBy(() -> useCase.execute(req, "supervisor1"))
            .isInstanceOf(AuditCodeAlreadyExistsException.class);
    }
}
