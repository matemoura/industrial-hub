package com.industrialhub.backend.common.webhook;

import com.industrialhub.backend.common.application.AuditService;
import com.industrialhub.backend.common.webhook.application.dto.WebhookSubscriptionResponse;
import com.industrialhub.backend.common.webhook.application.usecase.ActivateWebhookSubscriptionUseCase;
import com.industrialhub.backend.common.webhook.domain.DeliveryStatus;
import com.industrialhub.backend.common.webhook.domain.WebhookDelivery;
import com.industrialhub.backend.common.webhook.domain.WebhookEvent;
import com.industrialhub.backend.common.webhook.domain.WebhookNotFoundException;
import com.industrialhub.backend.common.webhook.domain.WebhookSubscription;
import com.industrialhub.backend.common.webhook.infrastructure.WebhookDeliveryRepository;
import com.industrialhub.backend.common.webhook.infrastructure.WebhookSubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ActivateWebhookSubscriptionUseCaseTest {

    @Mock private WebhookSubscriptionRepository repository;
    @Mock private WebhookDeliveryRepository deliveryRepository;
    @Mock private AuditService auditService;

    private ActivateWebhookSubscriptionUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ActivateWebhookSubscriptionUseCase(repository, deliveryRepository, auditService);
    }

    @Test
    void shouldActivateSubscription_andMarkPendingRetriesAsFailed() {
        UUID id = UUID.randomUUID();
        WebhookSubscription sub = WebhookSubscription.builder()
                .id(id).url("https://example.com/hook").active(false)
                .events(Set.of(WebhookEvent.NC_CREATED))
                .createdBy("admin").createdAt(LocalDateTime.now())
                .disabledAt(LocalDateTime.now())
                .build();

        WebhookDelivery d1 = WebhookDelivery.builder()
                .id(UUID.randomUUID()).subscription(sub).event(WebhookEvent.NC_CREATED)
                .attempt(2).status(DeliveryStatus.PENDING_RETRY).createdAt(LocalDateTime.now())
                .build();
        WebhookDelivery d2 = WebhookDelivery.builder()
                .id(UUID.randomUUID()).subscription(sub).event(WebhookEvent.NC_CREATED)
                .attempt(1).status(DeliveryStatus.PENDING_RETRY).createdAt(LocalDateTime.now())
                .build();

        when(repository.findById(id)).thenReturn(Optional.of(sub));
        when(deliveryRepository.findBySubscriptionIdAndStatus(id, DeliveryStatus.PENDING_RETRY))
                .thenReturn(List.of(d1, d2));
        when(repository.save(any())).thenReturn(sub);

        useCase.execute(id, "admin");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<WebhookDelivery>> captor = ArgumentCaptor.forClass(List.class);
        verify(deliveryRepository).saveAll(captor.capture());

        List<WebhookDelivery> saved = captor.getValue();
        assertThat(saved).hasSize(2);
        assertThat(saved).allMatch(d -> d.getStatus() == DeliveryStatus.FAILED);
        assertThat(saved).allMatch(d -> "Retry cancelado: subscription reativada".equals(d.getErrorMessage()));
        assertThat(sub.isActive()).isTrue();
        assertThat(sub.getDisabledAt()).isNull();
    }

    @Test
    void shouldActivateSubscription_whenNoPendingRetries() {
        UUID id = UUID.randomUUID();
        WebhookSubscription sub = WebhookSubscription.builder()
                .id(id).url("https://example.com/hook").active(false)
                .events(Set.of(WebhookEvent.NC_CREATED))
                .createdBy("admin").createdAt(LocalDateTime.now())
                .build();

        when(repository.findById(id)).thenReturn(Optional.of(sub));
        when(deliveryRepository.findBySubscriptionIdAndStatus(id, DeliveryStatus.PENDING_RETRY))
                .thenReturn(List.of());
        when(repository.save(any())).thenReturn(sub);

        useCase.execute(id, "admin");

        verify(deliveryRepository, never()).saveAll(anyList());
        assertThat(sub.isActive()).isTrue();
    }

    @Test
    void shouldThrow_whenSubscriptionNotFound() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(id, "admin"))
                .isInstanceOf(WebhookNotFoundException.class);
    }
}
