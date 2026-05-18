package com.industrialhub.backend.maintenance.application.dto;

import java.util.Map;

public record WoSummaryResponse(
    Map<String, Long> byStatus,
    Map<String, Long> byType
) {}
