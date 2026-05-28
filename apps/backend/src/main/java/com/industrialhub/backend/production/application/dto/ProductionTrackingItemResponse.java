package com.industrialhub.backend.production.application.dto;

import com.industrialhub.backend.production.domain.ProductionOrderDisplayStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A single production order card in the tracking kanban.
 * US-082 / ADR-041.
 */
public record ProductionTrackingItemResponse(
        String id,
        String dynamicsOrderNumber,
        String productName,
        String familyName,
        ProductionOrderDisplayStatus displayStatus,
        BigDecimal plannedQty,
        BigDecimal producedQty,
        LocalDate dueDate,
        boolean overdue
) {}
