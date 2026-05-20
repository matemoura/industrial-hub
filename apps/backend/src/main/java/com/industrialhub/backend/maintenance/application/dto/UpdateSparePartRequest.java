package com.industrialhub.backend.maintenance.application.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateSparePartRequest(
    @NotBlank @Size(max = 200) String name,
    @Size(max = 100) String category,
    @Size(max = 50, message = "unit deve ter no máximo 50 caracteres") String unit,
    @Min(0) Integer minStockQty
) {}
