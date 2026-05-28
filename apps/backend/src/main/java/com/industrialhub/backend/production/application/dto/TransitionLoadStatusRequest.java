package com.industrialhub.backend.production.application.dto;

import com.industrialhub.backend.production.domain.LoadStatus;
import jakarta.validation.constraints.NotNull;

public record TransitionLoadStatusRequest(
        @NotNull LoadStatus targetStatus
) {}
