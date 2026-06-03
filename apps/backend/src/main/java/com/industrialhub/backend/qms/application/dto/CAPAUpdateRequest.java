package com.industrialhub.backend.qms.application.dto;

import com.industrialhub.backend.qms.domain.ActionType;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

public record CAPAUpdateRequest(
    ActionType type,
    @Size(max = 2000) String rootCauseConfirmed,
    @Size(max = 2000) String preventiveMeasure,
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate effectivenessCheckDate
) {}
