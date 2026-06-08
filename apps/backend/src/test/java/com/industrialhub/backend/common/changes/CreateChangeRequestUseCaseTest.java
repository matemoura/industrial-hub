package com.industrialhub.backend.common.changes;

import com.industrialhub.backend.common.application.AuditService;
import com.industrialhub.backend.common.changes.application.dto.CreateChangeRequestRequest;
import com.industrialhub.backend.common.changes.application.usecase.CreateChangeRequestUseCase;
import com.industrialhub.backend.common.changes.domain.ChangeRequest;
import com.industrialhub.backend.common.changes.domain.ChangeRequestCodeConflictException;
import com.industrialhub.backend.common.changes.domain.ChangeStatus;
import com.industrialhub.backend.common.changes.domain.ChangeType;
import com.industrialhub.backend.common.changes.infrastructure.ChangeRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreateChangeRequestUseCaseTest {

    @Mock private ChangeRequestRepository changeRequestRepository;
    @Mock private AuditService auditService;

    private CreateChangeRequestUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new CreateChangeRequestUseCase(changeRequestRepository, auditService);
    }

    private CreateChangeRequestRequest buildRequest() {
        return new CreateChangeRequestRequest(
            "Título da mudança",
            "Descrição completa",
            ChangeType.PROCESS,
            "Justificativa técnica"
        );
    }

    @Test
    void acI_codigoAutoGerado_CR_ANO_001_paraPrimeiroDaAno() {
        // AC (i): Código auto-gerado CR-{ANO}-001 para primeiro do ano
        int year = LocalDate.now().getYear();
        String expectedPrefix = "CR-" + year + "-";
        when(changeRequestRepository.countByCodeStartingWith(expectedPrefix)).thenReturn(0L);
        when(changeRequestRepository.save(any())).thenAnswer(inv -> {
            ChangeRequest cr = inv.getArgument(0);
            cr.setId(UUID.randomUUID());
            cr.setCreatedAt(LocalDateTime.now());
            return cr;
        });

        var response = useCase.execute(buildRequest(), "user1");

        assertThat(response.code()).isEqualTo(expectedPrefix + "001");
        assertThat(response.status()).isEqualTo(ChangeStatus.DRAFT);
        assertThat(response.requestedBy()).isEqualTo("user1");
    }

    @Test
    void acJ_dataIntegrityViolation_lanca409ChangeRequestCodeConflictException() {
        // AC (j): DataIntegrityViolationException → 409
        int year = LocalDate.now().getYear();
        String expectedPrefix = "CR-" + year + "-";
        when(changeRequestRepository.countByCodeStartingWith(expectedPrefix)).thenReturn(0L);
        when(changeRequestRepository.save(any()))
            .thenThrow(new DataIntegrityViolationException("duplicate key"));

        assertThatThrownBy(() -> useCase.execute(buildRequest(), "user1"))
            .isInstanceOf(ChangeRequestCodeConflictException.class);
    }
}
