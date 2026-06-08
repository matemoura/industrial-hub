package com.industrialhub.backend.common.changes.application.dto;

import com.industrialhub.backend.common.changes.domain.ChangeType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateChangeRequestRequest(
    @NotBlank String title,
    @NotBlank String description,
    @NotNull ChangeType changeType,
    @NotBlank String justification
) {}
