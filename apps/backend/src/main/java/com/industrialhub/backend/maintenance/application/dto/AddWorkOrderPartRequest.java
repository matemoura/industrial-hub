package com.industrialhub.backend.maintenance.application.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AddWorkOrderPartRequest(
    @NotNull UUID sparePartId,
    @NotNull @Min(1) Integer quantity
) {}
