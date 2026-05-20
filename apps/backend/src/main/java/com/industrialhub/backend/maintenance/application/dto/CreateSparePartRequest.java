package com.industrialhub.backend.maintenance.application.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateSparePartRequest(
    @NotBlank String code,
    @NotBlank @Size(max = 200) String name,
    @Size(max = 100) String category,
    String unit,
    @NotNull @Min(0) Integer stockQty,
    @NotNull @Min(0) Integer minStockQty
) {}
