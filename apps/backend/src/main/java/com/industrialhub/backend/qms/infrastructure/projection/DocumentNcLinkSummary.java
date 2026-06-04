package com.industrialhub.backend.qms.infrastructure.projection;

import com.industrialhub.backend.qms.domain.NcDocumentLinkType;
import com.industrialhub.backend.qms.domain.NcSeverity;
import com.industrialhub.backend.qms.domain.NcStatus;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Sprint 39 / ADR-050 Decisão 2: projeção leve Documento→NCs.
 */
public interface DocumentNcLinkSummary {
    UUID getLinkId();
    UUID getNcId();
    String getNcTitle();
    NcSeverity getNcSeverity();
    NcStatus getNcStatus();
    NcDocumentLinkType getLinkType();
    LocalDateTime getLinkedAt();
}
