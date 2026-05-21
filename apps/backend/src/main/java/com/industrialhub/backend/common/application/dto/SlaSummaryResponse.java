package com.industrialhub.backend.common.application.dto;

public record SlaSummaryResponse(
    long totalBreachedNcs,
    long totalBreachedWorkOrders,
    long totalOpenNcs,
    long totalOpenWorkOrders
) {}
