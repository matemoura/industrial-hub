package com.industrialhub.backend.oee.application.dto;

import java.math.BigDecimal;

public record IndirectActivityDto(
        String description,
        long occurrences,
        BigDecimal totalHours,
        BigDecimal percentOfTotal
) {}
