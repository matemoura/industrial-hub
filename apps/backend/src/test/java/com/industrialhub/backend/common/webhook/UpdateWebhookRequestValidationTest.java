package com.industrialhub.backend.common.webhook;

import com.industrialhub.backend.common.webhook.application.dto.UpdateWebhookRequest;
import com.industrialhub.backend.common.webhook.domain.WebhookEvent;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SEC-096 AC#16: UpdateWebhookRequest — validação PATCH semântico para o campo url.
 * - url="" → violação com "URL não pode ser vazia"
 * - url=null → válido (PATCH semântico, sem alteração)
 * - url="https://example.com" → válido
 */
class UpdateWebhookRequestValidationTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidatorFactory() {
        validatorFactory.close();
    }

    @Test
    void url_empty_shouldHaveViolationWithMessage() {
        UpdateWebhookRequest request = new UpdateWebhookRequest(
                "",
                null,
                Set.of(WebhookEvent.NC_CREATED),
                null
        );

        Set<ConstraintViolation<UpdateWebhookRequest>> violations = validator.validate(request);

        assertThat(violations)
                .anyMatch(v -> v.getPropertyPath().toString().equals("url")
                        && v.getMessage().equals("URL não pode ser vazia"));
    }

    @Test
    void url_null_shouldHaveNoViolation() {
        UpdateWebhookRequest request = new UpdateWebhookRequest(
                null,
                null,
                Set.of(WebhookEvent.NC_CREATED),
                null
        );

        Set<ConstraintViolation<UpdateWebhookRequest>> violations = validator.validate(request);

        assertThat(violations)
                .noneMatch(v -> v.getPropertyPath().toString().equals("url"));
    }

    @Test
    void url_validHttps_shouldHaveNoViolation() {
        UpdateWebhookRequest request = new UpdateWebhookRequest(
                "https://example.com",
                null,
                Set.of(WebhookEvent.NC_CREATED),
                null
        );

        Set<ConstraintViolation<UpdateWebhookRequest>> violations = validator.validate(request);

        assertThat(violations)
                .noneMatch(v -> v.getPropertyPath().toString().equals("url"));
    }
}
