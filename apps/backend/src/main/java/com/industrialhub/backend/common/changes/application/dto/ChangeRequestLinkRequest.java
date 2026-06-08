package com.industrialhub.backend.common.changes.application.dto;

import com.industrialhub.backend.common.changes.domain.ChangeEntityType;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ChangeRequestLinkRequest(
    @NotNull ChangeEntityType entityType,
    @NotNull UUID entityId,
    String linkNote
) {}
