package com.industrialhub.backend.production.application.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Full kanban tracking response — ordered columns plus last sync timestamp.
 * US-082 / ADR-041.
 */
public record ProductionTrackingResponse(
        List<ProductionTrackingColumnResponse> columns,
        LocalDateTime lastSyncAt
) {}
