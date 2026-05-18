package com.industrialhub.backend.qms.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record CreateSupplierRequest(
    @NotBlank @Size(max = 50) String code,
    @NotBlank @Size(max = 200) String name,
    @Size(max = 100) String contactEmail,
    @Size(max = 20) String contactPhone,
    @Size(max = 200) String address,
    LocalDate onboardedAt
) {}
