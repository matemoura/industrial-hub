package com.industrialhub.backend.qms.complaints;

import com.industrialhub.backend.common.application.AuditService;
import com.industrialhub.backend.qms.complaints.application.dto.ComplaintResponse;
import com.industrialhub.backend.qms.complaints.application.dto.CreateComplaintRequest;
import com.industrialhub.backend.qms.complaints.application.usecase.CreateComplaintUseCase;
import com.industrialhub.backend.qms.complaints.domain.ComplaintCodeConflictException;
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
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreateComplaintUseCaseTest {

    @Mock private CustomerComplaintRepository complaintRepository;
    @Mock private AuditService auditService;

    private CreateComplaintUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new CreateComplaintUseCase(complaintRepository, auditService);
    }

    private CreateComplaintRequest buildRequest() {
        return new CreateComplaintRequest(
            "Reclamação de produto",
            "Produto apresentou defeito na embalagem",
            ComplaintSource.CLIENT,
            "PROD-001",
            "LOTE-2026",
            NcSeverity.HIGH,
            LocalDate.now(),
            "Cliente XPTO",
            "qualidade"
        );
    }

    @Test
    void acF_codigoAutoGerado_REC_ANO_001_paraPrimeiroDaAno() {
        int year = LocalDate.now().getYear();
        String expectedPrefix = "REC-" + year + "-";
        when(complaintRepository.countByCodeStartingWith(expectedPrefix)).thenReturn(0L);
        when(complaintRepository.save(any())).thenAnswer(inv -> {
            CustomerComplaint c = inv.getArgument(0);
            c.setId(UUID.randomUUID());
            c.setCreatedAt(LocalDateTime.now());
            return c;
        });

        ComplaintResponse response = useCase.execute(buildRequest(), "supervisor1");

        assertThat(response.code()).isEqualTo(expectedPrefix + "001");
        assertThat(response.status()).isEqualTo(ComplaintStatus.RECEIVED);
        assertThat(response.severity()).isEqualTo(NcSeverity.HIGH);
    }

    @Test
    void acG_dataIntegrityViolation_lancaComplaintCodeConflictException() {
        int year = LocalDate.now().getYear();
        String expectedPrefix = "REC-" + year + "-";
        when(complaintRepository.countByCodeStartingWith(expectedPrefix)).thenReturn(0L);
        when(complaintRepository.save(any()))
            .thenThrow(new DataIntegrityViolationException("duplicate key"));

        assertThatThrownBy(() -> useCase.execute(buildRequest(), "supervisor1"))
            .isInstanceOf(ComplaintCodeConflictException.class);
    }
}
