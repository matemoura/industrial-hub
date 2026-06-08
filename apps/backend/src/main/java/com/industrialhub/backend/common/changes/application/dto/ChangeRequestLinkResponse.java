package com.industrialhub.backend.common.changes.application.dto;

import com.industrialhub.backend.common.changes.domain.ChangeEntityType;
import com.industrialhub.backend.common.changes.domain.ChangeRequestLink;

import java.time.LocalDateTime;
import java.util.UUID;

public record ChangeRequestLinkResponse(
    UUID id,
    UUID changeRequestId,
    ChangeEntityType entityType,
    UUID entityId,
    String linkNote,
    LocalDateTime createdAt
) {
    public static ChangeRequestLinkResponse from(ChangeRequestLink link) {
        return new ChangeRequestLinkResponse(
            link.getId(),
            link.getChangeRequest().getId(),
            link.getEntityType(),
            link.getEntityId(),
            link.getLinkNote(),
            link.getCreatedAt()
        );
    }
}
