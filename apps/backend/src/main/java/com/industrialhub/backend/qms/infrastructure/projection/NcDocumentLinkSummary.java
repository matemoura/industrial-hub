package com.industrialhub.backend.qms.infrastructure.projection;

import com.industrialhub.backend.qms.domain.NcDocumentLinkType;
import com.industrialhub.backend.qms.ged.domain.DocumentCategory;
import com.industrialhub.backend.qms.ged.domain.DocumentStatus;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Sprint 39 / ADR-050 Decisão 2: projeção leve NC→Documentos.
 * linkedBy não exposto (ADR-049 §4).
 */
public interface NcDocumentLinkSummary {
    UUID getLinkId();
    UUID getDocumentId();
    String getDocumentCode();
    String getDocumentTitle();
    DocumentCategory getDocumentCategory();
    DocumentStatus getDocumentStatus();
    NcDocumentLinkType getLinkType();
    LocalDateTime getLinkedAt();
}
