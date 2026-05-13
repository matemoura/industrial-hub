package com.industrialhub.backend.qms.application.dto;

import com.industrialhub.backend.qms.domain.ActionStatus;
import com.industrialhub.backend.qms.domain.CorrectiveAction;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record ActionResponse(
    UUID id,
    UUID ncId,
    String description,
    String responsible,
    LocalDate dueDate,
    ActionStatus status,
    LocalDateTime completedAt,
    String completedBy
) {
    public static ActionResponse from(CorrectiveAction action) {
        return new ActionResponse(
            action.getId(),
            action.getNonConformance().getId(),
            action.getDescription(),
            action.getResponsible(),
            action.getDueDate(),
            action.getStatus(),
            action.getCompletedAt(),
            action.getCompletedBy()
        );
    }
}
