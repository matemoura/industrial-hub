package com.industrialhub.backend.common.webhook;

import com.industrialhub.backend.common.application.AuditService;
import com.industrialhub.backend.common.application.dto.webhook.NcWebhookPayload;
import com.industrialhub.backend.common.webhook.domain.WebhookEvent;
import com.industrialhub.backend.common.webhook.service.WebhookDispatchService;
import com.industrialhub.backend.qms.application.dto.CreateNcRequest;
import com.industrialhub.backend.qms.application.dto.NcResponse;
import com.industrialhub.backend.qms.application.usecase.CreateNcUseCase;
import com.industrialhub.backend.qms.application.usecase.QmsEmailService;
import com.industrialhub.backend.qms.domain.NcSeverity;
import com.industrialhub.backend.qms.domain.NcStatus;
import com.industrialhub.backend.qms.domain.NcType;
import com.industrialhub.backend.qms.domain.NonConformance;
import com.industrialhub.backend.qms.infrastructure.NonConformanceRepository;
import com.industrialhub.backend.qms.infrastructure.SupplierRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CreateNcUseCaseWebhookTest {

    @Mock private NonConformanceRepository repository;
    @Mock private SupplierRepository supplierRepository;
    @Mock private QmsEmailService emailService;
    @Mock private AuditService auditService;
    @Mock private WebhookDispatchService webhookDispatchService;

    @InjectMocks private CreateNcUseCase useCase;

    @Test
    void execute_nonCriticalNc_dispatchesNcCreatedOnly() {
        CreateNcRequest request = new CreateNcRequest(
                "NC Test", "Description", NcType.PROCESS, NcSeverity.LOW, null);

        NonConformance saved = NonConformance.builder()
                .id(UUID.randomUUID())
                .title("NC Test")
                .type(NcType.PROCESS)
                .severity(NcSeverity.LOW)
                .status(NcStatus.OPEN)
                .reportedBy("operator1")
                .reportedAt(LocalDateTime.now())
                .build();

        when(repository.save(any())).thenReturn(saved);

        useCase.execute(request, "operator1");

        verify(webhookDispatchService).dispatch(eq(WebhookEvent.NC_CREATED), any(NcWebhookPayload.class));
        verify(webhookDispatchService, never()).dispatch(eq(WebhookEvent.NC_CRITICAL_OPENED), any());
    }

    @Test
    void execute_criticalNc_dispatchesBothEvents() {
        CreateNcRequest request = new CreateNcRequest(
                "Critical NC", null, NcType.EQUIPMENT, NcSeverity.CRITICAL, null);

        NonConformance saved = NonConformance.builder()
                .id(UUID.randomUUID())
                .title("Critical NC")
                .type(NcType.EQUIPMENT)
                .severity(NcSeverity.CRITICAL)
                .status(NcStatus.OPEN)
                .reportedBy("operator1")
                .reportedAt(LocalDateTime.now())
                .build();

        when(repository.save(any())).thenReturn(saved);

        useCase.execute(request, "operator1");

        verify(webhookDispatchService).dispatch(eq(WebhookEvent.NC_CREATED), any(NcWebhookPayload.class));
        verify(webhookDispatchService).dispatch(eq(WebhookEvent.NC_CRITICAL_OPENED), any(NcWebhookPayload.class));
    }
}
