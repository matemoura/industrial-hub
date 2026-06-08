package com.industrialhub.backend.common.changes.application.dto;

import jakarta.validation.constraints.NotNull;

public record ApproveChangeRequestRequest(
    @NotNull Boolean approved,
    String rejectionReason
) {}
