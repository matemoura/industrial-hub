package com.industrialhub.backend.production.application.dto;

import jakarta.validation.constraints.NotBlank;

public record RejectMrpSuggestionRequest(
        @NotBlank String reason
) {}
