package com.industrialhub.backend.production.application.dto;

import com.industrialhub.backend.production.domain.CycleTime;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record CycleTimeResponse(
        UUID id,
        UUID productId,
        String productCode,
        Double secondsPerUnit,
        LocalDate effectiveDate,
        String importedBy,
        LocalDateTime importedAt
) {
    public static CycleTimeResponse from(CycleTime c) {
        return new CycleTimeResponse(
                c.getId(),
                c.getProduct().getId(),
                c.getProduct().getDynamicsCode(),
                c.getSecondsPerUnit(),
                c.getEffectiveDate(),
                c.getImportedBy(),
                c.getImportedAt()
        );
    }
}
