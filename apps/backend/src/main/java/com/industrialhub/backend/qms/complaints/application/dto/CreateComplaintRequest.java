package com.industrialhub.backend.qms.complaints.application.dto;

import com.industrialhub.backend.qms.complaints.domain.ComplaintSource;
import com.industrialhub.backend.qms.domain.NcSeverity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record CreateComplaintRequest(
    @NotBlank String title,
    @NotBlank String description,
    @NotNull ComplaintSource source,
    String productCode,
    String batchNumber,
    @NotNull NcSeverity severity,
    @NotNull LocalDate reportedDate,
    @NotBlank String reportedBy,
    @NotBlank String assignedTo
) {}
