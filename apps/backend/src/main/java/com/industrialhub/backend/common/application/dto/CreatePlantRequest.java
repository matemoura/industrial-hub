package com.industrialhub.backend.common.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreatePlantRequest(
    @NotBlank @Size(max = 50) String code,
    @NotBlank @Size(max = 200) String name,
    @Size(max = 200) String address,
    @Size(max = 50) String timezone
) {}
