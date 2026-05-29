package com.industrialhub.backend.production.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RejectMrpSuggestionRequest(
        @NotBlank
        @Size(max = 500, message = "Motivo deve ter no máximo 500 caracteres")
        String reason
) {}
