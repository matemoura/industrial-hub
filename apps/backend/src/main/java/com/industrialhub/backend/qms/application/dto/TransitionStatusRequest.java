package com.industrialhub.backend.qms.application.dto;

import com.industrialhub.backend.qms.domain.NcStatus;
import jakarta.validation.constraints.NotNull;

public record TransitionStatusRequest(@NotNull NcStatus status) {}
