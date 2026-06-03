package com.industrialhub.backend.qms.application.dto;

import com.industrialhub.backend.qms.domain.ActionStatus;
import com.industrialhub.backend.qms.domain.ActionType;

import java.time.LocalDate;
import java.util.UUID;

public record CAPASummaryResponse(
    UUID actionId,
    UUID ncCode,
    String ncTitle,
    String description,
    ActionType type,
    ActionStatus status,
    String responsible,
    LocalDate dueDate,
    LocalDate effectivenessCheckDate
) {}
