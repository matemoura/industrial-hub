package com.industrialhub.backend.qms.ged.application.dto;

import com.industrialhub.backend.qms.ged.domain.DocumentCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateDocumentRequest(
    /**
     * SEC-126: Pattern restricts code to uppercase letters, digits and hyphens,
     * preventing path traversal in the MinIO storage path.
     */
    @NotBlank
    @Size(max = 20)
    @Pattern(
        regexp = "^[A-Z0-9\\-]{3,20}$",
        message = "Código deve conter apenas letras maiúsculas, números e hífens (3–20 caracteres)"
    )
    String code,

    @NotBlank
    String title,

    @NotNull
    DocumentCategory category,

    @NotBlank
    @Size(max = 1000)
    String changeReason
) {}
