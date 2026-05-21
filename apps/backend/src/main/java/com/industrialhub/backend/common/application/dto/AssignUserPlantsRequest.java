package com.industrialhub.backend.common.application.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record AssignUserPlantsRequest(
    @NotNull
    @Size(max = 100, message = "plantIds não pode conter mais de 100 plantas")
    List<UUID> plantIds
) {}
