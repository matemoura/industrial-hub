package com.industrialhub.backend.production.application.dto;

import com.industrialhub.backend.production.domain.LoadStatus;
import com.industrialhub.backend.production.domain.SterilizationLoad;
import com.industrialhub.backend.production.domain.SterilizationMethod;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record SterilizationLoadResponse(
        UUID id,
        String loadNumber,
        LoadStatus status,
        SterilizationMethod method,
        String sterilizerName,        // nullable
        LocalDate sterilizationDate,
        String batchCode,
        String notes,
        String createdBy,
        LocalDateTime createdAt,
        LocalDateTime closedAt,
        LocalDateTime releasedAt,
        Integer totalOrders           // US-100 — ADR-043 Decisão 5 via @Formula
) {
    public static SterilizationLoadResponse from(SterilizationLoad sl) {
        return new SterilizationLoadResponse(
                sl.getId(),
                sl.getLoadNumber(),
                sl.getStatus(),
                sl.getMethod(),
                sl.getSterilizer() != null ? sl.getSterilizer().getName() : null,
                sl.getSterilizationDate(),
                sl.getBatchCode(),
                sl.getNotes(),
                sl.getCreatedBy(),
                sl.getCreatedAt(),
                sl.getClosedAt(),
                sl.getReleasedAt(),
                sl.getTotalOrders() != null ? sl.getTotalOrders() : 0
        );
    }
}
