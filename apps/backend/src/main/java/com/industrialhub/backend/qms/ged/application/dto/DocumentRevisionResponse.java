package com.industrialhub.backend.qms.ged.application.dto;

import com.industrialhub.backend.qms.ged.domain.DocumentRevision;

import java.time.LocalDateTime;
import java.util.UUID;

public record DocumentRevisionResponse(
    UUID id,
    String revisionNumber,
    String originalFileName,
    long fileSizeBytes,
    String uploadedBy,
    LocalDateTime uploadedAt,
    String changeReason
) {
    public static DocumentRevisionResponse from(DocumentRevision rev) {
        return new DocumentRevisionResponse(
            rev.getId(),
            rev.getRevisionNumber(),
            rev.getOriginalFileName(),
            rev.getFileSizeBytes(),
            rev.getUploadedBy(),
            rev.getUploadedAt(),
            rev.getChangeReason()
        );
    }
}
