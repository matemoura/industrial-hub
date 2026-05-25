package com.industrialhub.backend.qms;

import com.industrialhub.backend.common.application.AuditService;
import com.industrialhub.backend.common.webhook.service.WebhookDispatchService;
import com.industrialhub.backend.qms.application.dto.CreateNcRequest;
import com.industrialhub.backend.qms.application.dto.NcResponse;
import com.industrialhub.backend.qms.application.usecase.CreateNcUseCase;
import com.industrialhub.backend.qms.domain.NcSeverity;
import com.industrialhub.backend.qms.domain.NcStatus;
import com.industrialhub.backend.qms.domain.NcType;
import com.industrialhub.backend.qms.domain.NonConformance;
import com.industrialhub.backend.qms.domain.Supplier;
import com.industrialhub.backend.qms.domain.SupplierRequiredForNcException;
import com.industrialhub.backend.qms.application.usecase.QmsEmailService;
import com.industrialhub.backend.qms.infrastructure.NonConformanceRepository;
import com.industrialhub.backend.qms.infrastructure.SupplierRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreateNcUseCaseTest {

    @Mock
    private NonConformanceRepository repository;

    @Mock
    private SupplierRepository supplierRepository;

    @Mock
    private QmsEmailService emailService;

    @Mock
    private AuditService auditService;

    @Mock
    private WebhookDispatchService webhookDispatchService;

    private CreateNcUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new CreateNcUseCase(repository, supplierRepository, emailService, auditService, webhookDispatchService);
    }

    @Test
    void shouldCreateNcWithStatusOpenAndReportedBy() {
        CreateNcRequest request = new CreateNcRequest(
            "Peça fora de tolerância",
            "Diâmetro 2mm acima do especificado",
            NcType.PRODUCT,
            NcSeverity.HIGH,
            null
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
            NcSeverity.LOW,
            null
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

    @Test
    void shouldThrow400_whenTypeIsSupplierAndSupplierIdIsAbsent() {
        CreateNcRequest request = new CreateNcRequest(
            "Peça defeituosa do fornecedor",
            null,
            NcType.SUPPLIER,
            NcSeverity.HIGH,
            null
        );

        assertThatThrownBy(() -> useCase.execute(request, "operator1"))
                .isInstanceOf(SupplierRequiredForNcException.class)
                .hasMessageContaining("supplierId é obrigatório");
    }

    @Test
    void shouldAssociateSupplier_whenTypeIsSupplierAndSupplierIdProvided() {
        UUID supplierId = UUID.randomUUID();
        Supplier supplier = Supplier.builder().id(supplierId).name("Acme Ltda").active(true).build();

        CreateNcRequest request = new CreateNcRequest(
            "Peça defeituosa",
            null,
            NcType.SUPPLIER,
            NcSeverity.MEDIUM,
            supplierId
        );

        NonConformance saved = NonConformance.builder()
                .id(UUID.randomUUID())
                .title(request.title())
                .type(request.type())
                .severity(request.severity())
                .status(NcStatus.OPEN)
                .reportedBy("operator1")
                .reportedAt(LocalDateTime.now())
                .supplier(supplier)
                .build();

        when(supplierRepository.findById(supplierId)).thenReturn(Optional.of(supplier));
        when(repository.save(any(NonConformance.class))).thenReturn(saved);

        NcResponse response = useCase.execute(request, "operator1");

        ArgumentCaptor<NonConformance> captor = ArgumentCaptor.forClass(NonConformance.class);
        verify(repository).save(captor.capture());

        assertThat(captor.getValue().getSupplier()).isNotNull();
        assertThat(captor.getValue().getSupplier().getId()).isEqualTo(supplierId);
        assertThat(response.supplierId()).isEqualTo(supplierId);
    }

    @Test
    void shouldIgnoreSupplierIdForNonSupplierTypes() {
        UUID supplierId = UUID.randomUUID();

        CreateNcRequest request = new CreateNcRequest(
            "Falha de processo",
            null,
            NcType.PROCESS,
            NcSeverity.LOW,
            supplierId
        );

        NonConformance saved = NonConformance.builder()
                .id(UUID.randomUUID())
                .title(request.title())
                .type(request.type())
                .severity(request.severity())
                .status(NcStatus.OPEN)
                .reportedBy("operator1")
                .reportedAt(LocalDateTime.now())
                .build();

        when(repository.save(any(NonConformance.class))).thenReturn(saved);

        useCase.execute(request, "operator1");

        ArgumentCaptor<NonConformance> captor = ArgumentCaptor.forClass(NonConformance.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getSupplier()).isNull();
    }
}
