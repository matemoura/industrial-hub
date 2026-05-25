package com.industrialhub.backend.common.webhook;

import com.industrialhub.backend.common.webhook.application.dto.CreateWebhookRequest;
import com.industrialhub.backend.common.webhook.application.dto.WebhookSubscriptionResponse;
import com.industrialhub.backend.common.webhook.application.usecase.ActivateWebhookSubscriptionUseCase;
import com.industrialhub.backend.common.webhook.application.usecase.CreateWebhookSubscriptionUseCase;
import com.industrialhub.backend.common.webhook.application.usecase.DeleteWebhookSubscriptionUseCase;
import com.industrialhub.backend.common.webhook.application.usecase.GetWebhookDeliveriesUseCase;
import com.industrialhub.backend.common.webhook.application.usecase.ListWebhookSubscriptionsUseCase;
import com.industrialhub.backend.common.webhook.application.usecase.TestWebhookUseCase;
import com.industrialhub.backend.common.webhook.application.usecase.UpdateWebhookSubscriptionUseCase;
import com.industrialhub.backend.common.webhook.domain.WebhookEvent;
import com.industrialhub.backend.common.webhook.presentation.WebhookController;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebhookControllerValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Mock private CreateWebhookSubscriptionUseCase createUseCase;
    @Mock private UpdateWebhookSubscriptionUseCase updateUseCase;
    @Mock private DeleteWebhookSubscriptionUseCase deleteUseCase;
    @Mock private ListWebhookSubscriptionsUseCase listUseCase;
    @Mock private GetWebhookDeliveriesUseCase deliveriesUseCase;
    @Mock private TestWebhookUseCase testUseCase;
    @Mock private ActivateWebhookSubscriptionUseCase activateUseCase;

    @InjectMocks private WebhookController controller;

    @Test
    void createWebhookRequest_invalidHttpUrl_failsValidation() {
        CreateWebhookRequest request = new CreateWebhookRequest(
                "http://evil.com/hook",
                null,
                Set.of(WebhookEvent.NC_CREATED),
                null
        );

        Set<ConstraintViolation<CreateWebhookRequest>> violations = validator.validate(request);
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("url"));
    }

    @Test
    void createWebhookRequest_httpsUrl_passesValidation() {
        CreateWebhookRequest request = new CreateWebhookRequest(
                "https://example.com/hook",
                null,
                Set.of(WebhookEvent.NC_CREATED),
                null
        );

        Set<ConstraintViolation<CreateWebhookRequest>> violations = validator.validate(request);
        assertThat(violations).isEmpty();
    }

    @Test
    void createWebhookRequest_localhostUrl_passesValidation() {
        CreateWebhookRequest request = new CreateWebhookRequest(
                "http://localhost:8080/hook",
                null,
                Set.of(WebhookEvent.NC_CREATED),
                null
        );

        Set<ConstraintViolation<CreateWebhookRequest>> violations = validator.validate(request);
        assertThat(violations).isEmpty();
    }

    @Test
    void createWebhookRequest_emptyEvents_failsValidation() {
        CreateWebhookRequest request = new CreateWebhookRequest(
                "https://example.com/hook",
                null,
                Set.of(),
                null
        );

        Set<ConstraintViolation<CreateWebhookRequest>> violations = validator.validate(request);
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("events"));
    }

    @Test
    void createWebhookRequest_nullEvents_failsValidation() {
        CreateWebhookRequest request = new CreateWebhookRequest(
                "https://example.com/hook",
                null,
                null,
                null
        );

        Set<ConstraintViolation<CreateWebhookRequest>> violations = validator.validate(request);
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("events"));
    }

    @Test
    void createWebhookRequest_blankUrl_failsValidation() {
        CreateWebhookRequest request = new CreateWebhookRequest(
                "",
                null,
                Set.of(WebhookEvent.NC_CREATED),
                null
        );

        Set<ConstraintViolation<CreateWebhookRequest>> violations = validator.validate(request);
        assertThat(violations).isNotEmpty();
    }

    @Test
    void createWebhook_invokesUseCase() {
        CreateWebhookRequest request = new CreateWebhookRequest(
                "https://example.com/hook",
                null,
                Set.of(WebhookEvent.NC_CREATED),
                "test"
        );

        WebhookSubscriptionResponse mockResponse = new WebhookSubscriptionResponse(
                UUID.randomUUID(), "https://example.com/hook", false,
                Set.of(WebhookEvent.NC_CREATED), true, "test",
                "admin", LocalDateTime.now(), null, null
        );

        when(createUseCase.execute(any(), any())).thenReturn(mockResponse);

        // Simulate call with a mock UserDetails principal
        org.springframework.security.core.userdetails.User user =
                new org.springframework.security.core.userdetails.User("admin", "", java.util.List.of());
        WebhookSubscriptionResponse result = controller.create(request, user);

        assertThat(result.url()).isEqualTo("https://example.com/hook");
    }
}
