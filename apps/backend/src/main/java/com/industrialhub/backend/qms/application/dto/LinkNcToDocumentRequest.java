package com.industrialhub.backend.qms.application.dto;

import com.industrialhub.backend.qms.domain.NcDocumentLinkType;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Sprint 39 / ADR-050 Decisão 3: request para vincular NC a Documento GED.
 */
public record LinkNcToDocumentRequest(
        @NotNull UUID documentId,
        @NotNull NcDocumentLinkType linkType
) {}
