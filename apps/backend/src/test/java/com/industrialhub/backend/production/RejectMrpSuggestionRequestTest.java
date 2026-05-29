package com.industrialhub.backend.production;

import com.industrialhub.backend.production.application.dto.RejectMrpSuggestionRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * US-103 SEC-116 — valida restrições do DTO RejectMrpSuggestionRequest.
 * Garante que o campo `reason` respeita @NotBlank e @Size(max=500).
 */
class RejectMrpSuggestionRequestTest {

    private static final Validator VALIDATOR =
            Validation.buildDefaultValidatorFactory().getValidator();

    /**
     * SEC-116 AC: motivo com 501 chars deve gerar ConstraintViolation com a mensagem correta.
     * Sem este teste o @Size(max=500) poderia ser removido sem detecção automática.
     */
    @Test
    void shouldViolate_whenReasonExceeds500Chars() {
        RejectMrpSuggestionRequest request = new RejectMrpSuggestionRequest("a".repeat(501));

        Set<ConstraintViolation<RejectMrpSuggestionRequest>> violations = VALIDATOR.validate(request);

        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage())
                .isEqualTo("Motivo deve ter no máximo 500 caracteres");
    }

    /**
     * Exatamente 500 caracteres é válido (boundary value — inclusive upper bound).
     */
    @Test
    void shouldPass_whenReasonIsExactly500Chars() {
        RejectMrpSuggestionRequest request = new RejectMrpSuggestionRequest("a".repeat(500));

        Set<ConstraintViolation<RejectMrpSuggestionRequest>> violations = VALIDATOR.validate(request);

        assertThat(violations).isEmpty();
    }

    /**
     * @NotBlank: campo vazio/blank deve ser rejeitado independente do @Size.
     */
    @Test
    void shouldViolate_whenReasonIsBlank() {
        RejectMrpSuggestionRequest request = new RejectMrpSuggestionRequest("   ");

        Set<ConstraintViolation<RejectMrpSuggestionRequest>> violations = VALIDATOR.validate(request);

        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath().toString())
                .isEqualTo("reason");
    }
}
