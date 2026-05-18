package com.industrialhub.backend.qms;

import com.industrialhub.backend.common.application.AuditService;
import com.industrialhub.backend.qms.application.dto.CreateSupplierRequest;
import com.industrialhub.backend.qms.application.dto.SupplierResponse;
import com.industrialhub.backend.qms.application.usecase.CreateSupplierUseCase;
import com.industrialhub.backend.qms.domain.Supplier;
import com.industrialhub.backend.qms.domain.SupplierDuplicateCodeException;
import com.industrialhub.backend.qms.infrastructure.SupplierRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreateSupplierUseCaseTest {

    @Mock
    private SupplierRepository repository;

    @Mock
    private AuditService auditService;

    private CreateSupplierUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new CreateSupplierUseCase(repository, auditService);
    }

    @Test
    void shouldCreateSupplierAndReturnResponse() {
        CreateSupplierRequest request = new CreateSupplierRequest(
            "FORN-001", "Acme Ltda", "contato@acme.com", null, null, null
        );

        Supplier saved = Supplier.builder()
                .id(UUID.randomUUID())
                .code("FORN-001")
                .name("Acme Ltda")
                .contactEmail("contato@acme.com")
                .active(true)
                .build();

        when(repository.existsByCode("FORN-001")).thenReturn(false);
        when(repository.save(any(Supplier.class))).thenReturn(saved);

        SupplierResponse response = useCase.execute(request, "admin");

        ArgumentCaptor<Supplier> captor = ArgumentCaptor.forClass(Supplier.class);
        verify(repository).save(captor.capture());

        assertThat(captor.getValue().getCode()).isEqualTo("FORN-001");
        assertThat(captor.getValue().getName()).isEqualTo("Acme Ltda");
        assertThat(captor.getValue().isActive()).isTrue();

        assertThat(response.code()).isEqualTo("FORN-001");
        assertThat(response.name()).isEqualTo("Acme Ltda");
        assertThat(response.active()).isTrue();
    }

    @Test
    void shouldThrow409WhenCodeAlreadyExists() {
        CreateSupplierRequest request = new CreateSupplierRequest(
            "FORN-001", "Acme Ltda", null, null, null, null
        );

        when(repository.existsByCode("FORN-001")).thenReturn(true);

        assertThatThrownBy(() -> useCase.execute(request, "admin"))
                .isInstanceOf(SupplierDuplicateCodeException.class)
                .hasMessageContaining("FORN-001");
    }

    @Test
    void shouldPersistOptionalFieldsAsNull_whenNotProvided() {
        CreateSupplierRequest request = new CreateSupplierRequest(
            "FORN-002", "Beta SA", null, null, null, null
        );

        Supplier saved = Supplier.builder()
                .id(UUID.randomUUID())
                .code("FORN-002")
                .name("Beta SA")
                .active(true)
                .build();

        when(repository.existsByCode("FORN-002")).thenReturn(false);
        when(repository.save(any(Supplier.class))).thenReturn(saved);

        useCase.execute(request, "admin");

        ArgumentCaptor<Supplier> captor = ArgumentCaptor.forClass(Supplier.class);
        verify(repository).save(captor.capture());

        assertThat(captor.getValue().getContactEmail()).isNull();
        assertThat(captor.getValue().getContactPhone()).isNull();
        assertThat(captor.getValue().getAddress()).isNull();
        assertThat(captor.getValue().getOnboardedAt()).isNull();
    }
}
