package com.industrialhub.backend.oee.application.parser;

import com.industrialhub.backend.oee.domain.RecordType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record ParsedRow(
        Long workerId,
        String workerName,
        LocalDate profileDate,
        LocalDateTime startTime,
        LocalDateTime endTime,
        RecordType recordType,
        String reference,
        Integer operationNumber,
        String workIdentifier,
        String description,
        BigDecimal hours
) {}
