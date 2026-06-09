package com.industrialhub.backend.qms.complaints.application.dto;

import com.industrialhub.backend.qms.complaints.domain.ComplaintSource;
import com.industrialhub.backend.qms.complaints.domain.ComplaintStatus;
import com.industrialhub.backend.qms.complaints.domain.CustomerComplaint;
import com.industrialhub.backend.qms.domain.NcSeverity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record ComplaintResponse(
    UUID id,
    String code,
    String title,
    String description,
    ComplaintSource source,
    String productCode,
    String batchNumber,
    NcSeverity severity,
    ComplaintStatus status,
    LocalDate reportedDate,
    String reportedBy,
    String assignedTo,
    String investigationSummary,
    String rootCause,
    String correctiveAction,
    boolean reportedToAnvisa,
    LocalDate anvisaReportDate,
    String anvisaReportNumber,
    UUID linkedNcId,
    String linkedNcCode,
    UUID linkedCapaId,
    String linkedCapaDescription,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    LocalDateTime closedAt
) {
    public static ComplaintResponse from(CustomerComplaint c, String linkedNcCode, String linkedCapaDescription) {
        return new ComplaintResponse(
            c.getId(),
            c.getCode(),
            c.getTitle(),
            c.getDescription(),
            c.getSource(),
            c.getProductCode(),
            c.getBatchNumber(),
            c.getSeverity(),
            c.getStatus(),
            c.getReportedDate(),
            c.getReportedBy(),
            c.getAssignedTo(),
            c.getInvestigationSummary(),
            c.getRootCause(),
            c.getCorrectiveAction(),
            c.isReportedToAnvisa(),
            c.getAnvisaReportDate(),
            c.getAnvisaReportNumber(),
            c.getLinkedNcId(),
            linkedNcCode,
            c.getLinkedCapaId(),
            linkedCapaDescription,
            c.getCreatedAt(),
            c.getUpdatedAt(),
            c.getClosedAt()
        );
    }

    public static ComplaintResponse from(CustomerComplaint c) {
        return from(c, null, null);
    }
}
