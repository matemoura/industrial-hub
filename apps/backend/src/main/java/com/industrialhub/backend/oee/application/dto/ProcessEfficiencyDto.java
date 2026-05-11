package com.industrialhub.backend.oee.application.dto;

import java.math.BigDecimal;

public record ProcessEfficiencyDto(
        String description,
        BigDecimal totalHours,
        long workerCount,
        long occurrences
) {}
