package com.industrialhub.backend.qms;

import com.industrialhub.backend.common.application.AuditService;
import com.industrialhub.backend.qms.application.dto.CreateNcRequest;
import com.industrialhub.backend.qms.application.dto.NcResponse;
import com.industrialhub.backend.qms.application.usecase.CreateNcUseCase;
import com.industrialhub.backend.qms.domain.NcSeverity;
import com.industrialhub.backend.qms.domain.NcStatus;
import com.industrialhub.backend.qms.domain.NcType;
import com.industrialhub.backend.qms.domain.NonConformance;
import com.industrialhub.backend.qms.application.usecase.QmsEmailService;
import com.industrialhub.backend.qms.infrastructure.NonConformanceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreateNcUseCaseTest {

    @Mock
    private NonConformanceRepository repository;

    @Mock
    private QmsEmailService emailService;

    @Mock
    private AuditService auditService;

    private CreateNcUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new CreateNcUseCase(repository, emailService, auditService);
    }

    @Test
    void shouldCreateNcWithStatusOpenAndReportedBy() {
        CreateNcRequest request = new CreateNcRequest(
            "Peça fora de tolerância",
            "Diâmetro 2mm acima do especificado",
            NcType.PRODUCT,
            NcSeverity.HIGH
        );

        NonConformance saved = NonConformance.builder()
                .id(UUID.randomUUID())
                .title(request.title())
                .description(request.description())
                .type(request.type())
                .severity(request.severity())
                .status(NcStatus.OPEN)
                .reportedBy("operator1")
                .reportedAt(LocalDateTime.now())
                .build();

        when(repository.save(any(NonConformance.class))).thenReturn(saved);

        NcResponse response = useCase.execute(request, "operator1");

        ArgumentCaptor<NonConformance> captor = ArgumentCaptor.forClass(NonConformance.class);
        verify(repository).save(captor.capture());

        NonConformance persisted = captor.getValue();
        assertThat(persisted.getStatus()).isEqualTo(NcStatus.OPEN);
        assertThat(persisted.getReportedBy()).isEqualTo("operator1");
        assertThat(persisted.getReportedAt()).isNotNull();
        assertThat(persisted.getClosedAt()).isNull();
        assertThat(persisted.getClosedBy()).isNull();

        assertThat(response.status()).isEqualTo(NcStatus.OPEN);
        assertThat(response.reportedBy()).isEqualTo("operator1");
    }

    @Test
    void shouldPersistDescriptionAsNull_whenNotProvided() {
        CreateNcRequest request = new CreateNcRequest(
            "Falha no processo",
            null,
            NcType.PROCESS,
            NcSeverity.LOW
        );

        NonConformance saved = NonConformance.builder()
                .id(UUID.randomUUID())
                .title(request.title())
                .type(request.type())
                .severity(request.severity())
                .status(NcStatus.OPEN)
                .reportedBy("supervisor1")
                .reportedAt(LocalDateTime.now())
                .build();

        when(repository.save(any(NonConformance.class))).thenReturn(saved);

        useCase.execute(request, "supervisor1");

        ArgumentCaptor<NonConformance> captor = ArgumentCaptor.forClass(NonConformance.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getDescription()).isNull();
    }
}
