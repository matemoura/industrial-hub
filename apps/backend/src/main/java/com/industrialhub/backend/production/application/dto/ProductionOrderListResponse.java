package com.industrialhub.backend.production.application.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Paginated list of production orders for the tracking table view.
 * Wraps standard page fields plus lastSyncAt from the latest import batch.
 * US-082 AC#6 / AC#8.
 */
public record ProductionOrderListResponse(
        List<ProductionOrderTrackingItemResponse> content,
        long totalElements,
        int totalPages,
        int number,
        LocalDateTime lastSyncAt
) {}
