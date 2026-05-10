package com.industrialhub.backend.oee.application.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record WorkerOeeDto(
        Long workerId,
        String workerName,
        LocalDate date,
        BigDecimal productiveHours,
        BigDecimal indirectHours,
        BigDecimal shiftDuration,
        BigDecimal availability
) {}
