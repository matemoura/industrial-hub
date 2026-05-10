package com.industrialhub.backend.oee.application.dto;

import java.math.BigDecimal;

public record PeriodSummaryDto(
        String period,
        BigDecimal avgAvailability,
        int workerCount
) {}
