package com.industrialhub.backend.qms.application.usecase;

import com.industrialhub.backend.common.application.AuditService;
import com.industrialhub.backend.common.domain.AuditAction;
import com.industrialhub.backend.qms.application.dto.LinkNcToDocumentRequest;
import com.industrialhub.backend.qms.application.dto.NcDocumentLinkResponse;
import com.industrialhub.backend.qms.domain.NcDocumentLink;
import com.industrialhub.backend.qms.domain.NcDocumentLinkAlreadyExistsException;
import com.industrialhub.backend.qms.domain.NcNotFoundException;
import com.industrialhub.backend.qms.domain.NonConformance;
import com.industrialhub.backend.qms.ged.domain.Document;
import com.industrialhub.backend.qms.ged.domain.DocumentNotFoundException;
import com.industrialhub.backend.qms.ged.domain.DocumentStatus;
import com.industrialhub.backend.qms.ged.infrastructure.DocumentRepository;
import com.industrialhub.backend.qms.infrastructure.NcDocumentLinkRepository;
import com.industrialhub.backend.qms.infrastructure.NonConformanceRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Sprint 39 / ADR-050 Decisão 2: vincula NC a Documento GED.
 * Documentos OBSOLETE não podem ser vinculados (422).
 * Vínculo duplicado → DataIntegrityViolationException → NcDocumentLinkAlreadyExistsException (409).
 */
@Service
@Transactional
public class LinkNcToDocumentUseCase {

    private final NonConformanceRepository nonConformanceRepository;
    private final DocumentRepository documentRepository;
    private final NcDocumentLinkRepository ncDocumentLinkRepository;
    private final AuditService auditService;

    public LinkNcToDocumentUseCase(NonConformanceRepository nonConformanceRepository,
                                   DocumentRepository documentRepository,
                                   NcDocumentLinkRepository ncDocumentLinkRepository,
                                   AuditService auditService) {
        this.nonConformanceRepository = nonConformanceRepository;
        this.documentRepository = documentRepository;
        this.ncDocumentLinkRepository = ncDocumentLinkRepository;
        this.auditService = auditService;
    }

    public NcDocumentLinkResponse execute(UUID ncId, LinkNcToDocumentRequest req, String principal) {
        NonConformance nc = nonConformanceRepository.findById(ncId)
                .orElseThrow(() -> new NcNotFoundException(ncId));

        Document doc = documentRepository.findById(req.documentId())
                .orElseThrow(() -> new DocumentNotFoundException(req.documentId()));

        if (doc.getStatus() == DocumentStatus.OBSOLETE) {
            throw new IllegalArgumentException(
                    "Não é possível vincular um documento com status OBSOLETE a uma NC.");
        }

        try {
            NcDocumentLink link = new NcDocumentLink(nc, doc, req.linkType(), principal);
            NcDocumentLink saved = ncDocumentLinkRepository.saveAndFlush(link);

            auditService.log(principal, AuditAction.NC_DOCUMENT_LINKED, "NcDocumentLink",
                    saved.getId(), Map.of(
                            "ncId", ncId.toString(),
                            "documentId", req.documentId().toString(),
                            "linkType", req.linkType().name()
                    ));

            // Build response directly from saved entity to avoid extra query with mock friction
            return new NcDocumentLinkResponse(
                    saved.getId(),
                    doc.getId(),
                    doc.getCode(),
                    doc.getTitle(),
                    doc.getCategory(),
                    doc.getStatus(),
                    saved.getLinkType(),
                    saved.getLinkedAt()
            );
        } catch (DataIntegrityViolationException e) {
            throw new NcDocumentLinkAlreadyExistsException(ncId, req.documentId());
        }
    }
}
