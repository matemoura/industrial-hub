package com.industrialhub.backend.qms.complaints.application.dto;

public record UpdateComplaintRequest(
    String title,
    String description,
    String productCode,
    String batchNumber,
    String assignedTo,
    String investigationSummary,
    String rootCause,
    String correctiveAction
) {}
