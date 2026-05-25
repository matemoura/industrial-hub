package com.industrialhub.backend.common.webhook;

import com.industrialhub.backend.common.application.AuditService;
import com.industrialhub.backend.common.domain.AuditAction;
import com.industrialhub.backend.common.webhook.application.dto.CreateWebhookRequest;
import com.industrialhub.backend.common.webhook.application.dto.WebhookSubscriptionResponse;
import com.industrialhub.backend.common.webhook.application.usecase.CreateWebhookSubscriptionUseCase;
import com.industrialhub.backend.common.webhook.domain.WebhookEvent;
import com.industrialhub.backend.common.webhook.domain.WebhookInvalidUrlException;
import com.industrialhub.backend.common.webhook.domain.WebhookSubscription;
import com.industrialhub.backend.common.webhook.infrastructure.WebhookSubscriptionRepository;
import com.industrialhub.backend.common.webhook.service.WebhookUrlValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreateWebhookSubscriptionUseCaseTest {

    @Mock private WebhookSubscriptionRepository repository;
    @Mock private WebhookUrlValidator webhookUrlValidator;
    @Mock private AuditService auditService;

    private CreateWebhookSubscriptionUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new CreateWebhookSubscriptionUseCase(repository, webhookUrlValidator, auditService);
    }

    @Test
    void execute_persistsAndReturnsResponse() {
        CreateWebhookRequest request = new CreateWebhookRequest(
                "https://example.com/hook",
                "my-secret",
                Set.of(WebhookEvent.NC_CREATED),
                "ERP integration"
        );

        WebhookSubscription saved = WebhookSubscription.builder()
                .id(UUID.randomUUID())
                .url(request.url())
                .secret(request.secret())
                .events(request.events())
                .description(request.description())
                .active(true)
                .createdBy("admin")
                .createdAt(LocalDateTime.now())
                .build();

        when(repository.save(any())).thenReturn(saved);

        WebhookSubscriptionResponse response = useCase.execute(request, "admin");

        assertThat(response.url()).isEqualTo("https://example.com/hook");
        assertThat(response.hasSecret()).isTrue();
        assertThat(response.active()).isTrue();
        assertThat(response.events()).contains(WebhookEvent.NC_CREATED);
        assertThat(response.createdBy()).isEqualTo("admin");

        ArgumentCaptor<WebhookSubscription> captor = ArgumentCaptor.forClass(WebhookSubscription.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getUrl()).isEqualTo("https://example.com/hook");
        assertThat(captor.getValue().getCreatedBy()).isEqualTo("admin");
    }

    @Test
    void execute_withoutSecret_hasSecretIsFalse() {
        CreateWebhookRequest request = new CreateWebhookRequest(
                "https://example.com/hook",
                null,
                Set.of(WebhookEvent.WORK_ORDER_CREATED),
                null
        );

        WebhookSubscription saved = WebhookSubscription.builder()
                .id(UUID.randomUUID())
                .url(request.url())
                .secret(null)
                .events(request.events())
                .active(true)
                .createdBy("admin")
                .createdAt(LocalDateTime.now())
                .build();

        when(repository.save(any())).thenReturn(saved);

        WebhookSubscriptionResponse response = useCase.execute(request, "admin");
        assertThat(response.hasSecret()).isFalse();
    }

    @Test
    void execute_rfc1918Url_throwsWebhookInvalidUrlExceptionBeforePersist() {
        CreateWebhookRequest request = new CreateWebhookRequest(
                "https://192.168.1.10/hook",
                null,
                Set.of(WebhookEvent.NC_CREATED),
                null
        );

        doThrow(new WebhookInvalidUrlException("URL aponta para endereço de rede privada ou reservada: 192.168.1.10"))
                .when(webhookUrlValidator).validate("https://192.168.1.10/hook");

        assertThatThrownBy(() -> useCase.execute(request, "admin"))
                .isInstanceOf(WebhookInvalidUrlException.class)
                .hasMessageContaining("192.168.1.10");

        verify(repository, never()).save(any());
    }

    @Test
    void execute_auditLogCalledWithCorrectActionAndPerformedBy() {
        String performedBy = "admin_user";
        CreateWebhookRequest request = new CreateWebhookRequest(
                "https://hooks.example.com/endpoint",
                null,
                Set.of(WebhookEvent.NC_CREATED),
                "audit test"
        );

        UUID savedId = UUID.randomUUID();
        WebhookSubscription saved = WebhookSubscription.builder()
                .id(savedId)
                .url(request.url())
                .secret(null)
                .events(request.events())
                .active(true)
                .createdBy(performedBy)
                .createdAt(LocalDateTime.now())
                .build();

        when(repository.save(any())).thenReturn(saved);

        useCase.execute(request, performedBy);

        verify(auditService).log(
                eq(performedBy),
                eq(AuditAction.WEBHOOK_CREATED),
                eq("WebhookSubscription"),
                eq(savedId.toString()),
                any(Map.class)
        );
    }
}
