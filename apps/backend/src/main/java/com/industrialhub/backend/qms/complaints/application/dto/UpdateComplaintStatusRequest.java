package com.industrialhub.backend.qms.complaints.application.dto;

import com.industrialhub.backend.qms.complaints.domain.ComplaintStatus;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record UpdateComplaintStatusRequest(
    @NotNull ComplaintStatus status,
    LocalDate completedDate
) {}
