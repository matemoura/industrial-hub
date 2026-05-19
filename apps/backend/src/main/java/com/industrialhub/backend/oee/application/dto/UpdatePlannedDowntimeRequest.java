package com.industrialhub.backend.oee.application.dto;

import com.industrialhub.backend.oee.domain.DowntimeReason;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.UUID;

public record UpdatePlannedDowntimeRequest(
        UUID equipmentId,

        @NotNull(message = "startAt é obrigatório")
        LocalDateTime startAt,

        @NotNull(message = "endAt é obrigatório")
        LocalDateTime endAt,

        @NotNull(message = "reason é obrigatório")
        DowntimeReason reason,

        String description
) {}
