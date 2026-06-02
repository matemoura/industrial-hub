package com.industrialhub.backend.qms.ged.application.dto;

import com.industrialhub.backend.qms.ged.domain.DocumentStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateDocumentStatusRequest(@NotNull DocumentStatus status) {}
