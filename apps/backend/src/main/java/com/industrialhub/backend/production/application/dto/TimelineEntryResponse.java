package com.industrialhub.backend.production.application.dto;

import java.time.LocalDate;
import java.util.UUID;

/** ADR-030 Decisão 7 — entrada da timeline Gantt simplificada */
public record TimelineEntryResponse(
        String orderNumber,
        String productCode,
        String productName,
        LocalDate startDate,
        LocalDate dueDate,
        Integer qty,
        String statusLabel,
        boolean isMrpSuggestion,
        boolean overdue,
        UUID suggestionId    // BUG-2 fix: UUID completo da MrpPlannedOrder (null para OPs Dynamics)
) {}
