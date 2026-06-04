package com.industrialhub.backend.qms.domain;

import java.util.UUID;

/**
 * Sprint 39 / ADR-050 Decisão 2: lançada quando a combinação (ncId, documentId)
 * já existe — unique constraint (nc_id, document_id) → 409 CONFLICT.
 */
public class NcDocumentLinkAlreadyExistsException extends RuntimeException {

    public NcDocumentLinkAlreadyExistsException(UUID ncId, UUID documentId) {
        super("Vínculo já existe entre NC " + ncId + " e documento " + documentId + ".");
    }
}
