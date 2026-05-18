package com.industrialhub.backend.qms.application.dto;

import com.industrialhub.backend.qms.domain.NcSeverity;
import com.industrialhub.backend.qms.domain.NcType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateNcRequest(
    @NotBlank @Size(max = 200) String title,
    String description,
    @NotNull NcType type,
    @NotNull NcSeverity severity,
    UUID supplierId
) {}
