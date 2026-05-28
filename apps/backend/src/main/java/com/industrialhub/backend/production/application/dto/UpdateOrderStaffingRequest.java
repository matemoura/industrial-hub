package com.industrialhub.backend.production.application.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record UpdateOrderStaffingRequest(
        @NotNull @Min(1) Integer plannedPeople
) {}
