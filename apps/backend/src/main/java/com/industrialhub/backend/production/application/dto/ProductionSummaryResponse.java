package com.industrialhub.backend.production.application.dto;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Count-by-status summary for the production tracking dashboard header.
 * US-082 / ADR-041.
 */
public record ProductionSummaryResponse(
        Map<String, Long> countByStatus,
        long totalActive,
        LocalDateTime lastSyncAt
) {}
