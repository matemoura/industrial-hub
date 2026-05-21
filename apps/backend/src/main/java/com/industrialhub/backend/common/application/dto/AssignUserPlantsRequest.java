package com.industrialhub.backend.common.application.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record AssignUserPlantsRequest(
    @NotNull List<UUID> plantIds
) {}
