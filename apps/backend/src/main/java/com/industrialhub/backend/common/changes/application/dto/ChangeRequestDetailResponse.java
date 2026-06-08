package com.industrialhub.backend.common.changes.application.dto;

import com.industrialhub.backend.common.changes.domain.ChangeRequest;
import com.industrialhub.backend.common.changes.domain.ChangeStatus;
import com.industrialhub.backend.common.changes.domain.ChangeType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record ChangeRequestDetailResponse(
    UUID id,
    String code,
    String title,
    String description,
    ChangeType changeType,
    String justification,
    String impactAssessment,
    ChangeStatus status,
    String requestedBy,
    LocalDateTime submittedAt,
    String reviewedBy,
    LocalDateTime reviewedAt,
    String approvedBy,
    LocalDateTime approvedAt,
    LocalDateTime implementedAt,
    String rejectionReason,
    LocalDateTime createdAt,
    List<ChangeRequestLinkResponse> links
) {
    public static ChangeRequestDetailResponse from(ChangeRequest cr, List<ChangeRequestLinkResponse> links) {
        return new ChangeRequestDetailResponse(
            cr.getId(),
            cr.getCode(),
            cr.getTitle(),
            cr.getDescription(),
            cr.getChangeType(),
            cr.getJustification(),
            cr.getImpactAssessment(),
            cr.getStatus(),
            cr.getRequestedBy(),
            cr.getSubmittedAt(),
            cr.getReviewedBy(),
            cr.getReviewedAt(),
            cr.getApprovedBy(),
            cr.getApprovedAt(),
            cr.getImplementedAt(),
            cr.getRejectionReason(),
            cr.getCreatedAt(),
            links
        );
    }
}
