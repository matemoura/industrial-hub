package com.industrialhub.backend.production.application.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AddOrderToLoadRequest(
        @NotNull UUID productionOrderId
) {}
