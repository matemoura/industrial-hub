package com.industrialhub.backend.maintenance.application.dto;

public record WorkOrderMetricsResponse(
        Double mttr,
        long totalOrders,
        long openOrders
) {}
