package com.industrialhub.backend.qms.application.dto;

import java.util.Map;

public record NcParetoResponse(
    Map<String, Long> byType,
    Map<String, Long> bySeverity
) {}
