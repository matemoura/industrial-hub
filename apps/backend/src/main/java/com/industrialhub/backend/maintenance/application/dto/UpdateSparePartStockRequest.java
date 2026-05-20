package com.industrialhub.backend.maintenance.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UpdateSparePartStockRequest(
    @NotNull Integer quantity,
    @NotBlank String reason
) {}
