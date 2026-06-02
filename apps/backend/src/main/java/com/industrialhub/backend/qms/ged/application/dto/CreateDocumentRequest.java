package com.industrialhub.backend.qms.ged.application.dto;

import com.industrialhub.backend.qms.ged.domain.DocumentCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateDocumentRequest(
    @NotBlank String code,
    @NotBlank String title,
    @NotNull DocumentCategory category,
    @NotBlank String changeReason
) {}
