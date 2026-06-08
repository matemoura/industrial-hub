package com.industrialhub.backend.common.changes.application.dto;

import jakarta.validation.constraints.NotNull;

public record ReviewChangeRequestRequest(
    String impactAssessment,
    @NotNull Boolean recommendApproval
) {}
