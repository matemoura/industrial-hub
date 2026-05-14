package com.industrialhub.backend.maintenance.application.dto;

import com.industrialhub.backend.maintenance.domain.EquipmentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record CreateEquipmentRequest(
    @NotBlank @Size(max = 50) String code,
    @NotBlank @Size(max = 200) String name,
    @Size(max = 100) String location,
    @NotNull EquipmentType type,
    LocalDate acquiredAt
) {}
