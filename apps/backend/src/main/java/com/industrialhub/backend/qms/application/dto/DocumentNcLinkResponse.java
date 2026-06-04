package com.industrialhub.backend.qms.application.dto;

import com.industrialhub.backend.qms.domain.NcDocumentLinkType;
import com.industrialhub.backend.qms.domain.NcSeverity;
import com.industrialhub.backend.qms.domain.NcStatus;
import com.industrialhub.backend.qms.infrastructure.projection.DocumentNcLinkSummary;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Sprint 39 / ADR-050 Decisão 3: response da visão inversa Documento→NCs.
 */
public record DocumentNcLinkResponse(
        UUID linkId,
        UUID ncId,
        String ncTitle,
        NcSeverity ncSeverity,
        NcStatus ncStatus,
        NcDocumentLinkType linkType,
        LocalDateTime linkedAt
) {
    public static DocumentNcLinkResponse from(DocumentNcLinkSummary s) {
        return new DocumentNcLinkResponse(
                s.getLinkId(),
                s.getNcId(),
                s.getNcTitle(),
                s.getNcSeverity(),
                s.getNcStatus(),
                s.getLinkType(),
                s.getLinkedAt()
        );
    }
}
