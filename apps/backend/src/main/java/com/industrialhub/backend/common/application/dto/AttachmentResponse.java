package com.industrialhub.backend.common.application.dto;

import com.industrialhub.backend.common.domain.Attachment;
import java.time.LocalDateTime;
import java.util.UUID;

public record AttachmentResponse(
    UUID id,
    String entityType,
    String entityId,
    String originalName,
    String contentType,
    long fileSizeBytes,
    String uploadedBy,
    LocalDateTime uploadedAt
) {
    public static AttachmentResponse from(Attachment a) {
        return new AttachmentResponse(a.getId(), a.getEntityType(), a.getEntityId(),
            a.getOriginalName(), a.getContentType(), a.getFileSizeBytes(),
            a.getUploadedBy(), a.getUploadedAt());
    }
}
