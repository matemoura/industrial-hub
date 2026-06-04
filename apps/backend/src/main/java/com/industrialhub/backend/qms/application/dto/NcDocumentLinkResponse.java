package com.industrialhub.backend.qms.application.dto;

import com.industrialhub.backend.qms.domain.NcDocumentLinkType;
import com.industrialhub.backend.qms.ged.domain.DocumentCategory;
import com.industrialhub.backend.qms.ged.domain.DocumentStatus;
import com.industrialhub.backend.qms.infrastructure.projection.NcDocumentLinkSummary;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Sprint 39 / ADR-050 Decisão 3: response de vínculo NC↔GED.
 * linkedBy NÃO incluído (ADR-049 §4).
 */
public record NcDocumentLinkResponse(
        UUID linkId,
        UUID documentId,
        String documentCode,
        String documentTitle,
        DocumentCategory documentCategory,
        DocumentStatus documentStatus,
        NcDocumentLinkType linkType,
        LocalDateTime linkedAt
) {
    public static NcDocumentLinkResponse from(NcDocumentLinkSummary s) {
        return new NcDocumentLinkResponse(
                s.getLinkId(),
                s.getDocumentId(),
                s.getDocumentCode(),
                s.getDocumentTitle(),
                s.getDocumentCategory(),
                s.getDocumentStatus(),
                s.getLinkType(),
                s.getLinkedAt()
        );
    }
}
