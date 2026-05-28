package com.industrialhub.backend.production.application.dto;

import com.industrialhub.backend.production.domain.ProductionOrderDisplayStatus;

import java.util.List;

/**
 * One column of the tracking kanban — a display status bucket with its items.
 * US-082 / ADR-041 Decisão 4 (max 50 items; truncated flag).
 */
public record ProductionTrackingColumnResponse(
        ProductionOrderDisplayStatus status,
        List<ProductionTrackingItemResponse> items,
        boolean truncated,
        long total
) {}
