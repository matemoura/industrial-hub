package com.industrialhub.backend.qms.application.usecase;

import com.industrialhub.backend.common.application.AuditService;
import com.industrialhub.backend.common.domain.AuditAction;
import com.industrialhub.backend.qms.domain.NcDocumentLink;
import com.industrialhub.backend.qms.domain.NcNotFoundException;
import com.industrialhub.backend.qms.infrastructure.NcDocumentLinkRepository;
import com.industrialhub.backend.qms.infrastructure.NonConformanceRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * Sprint 39 / ADR-050 Decisão 2: remove vínculo NC↔Documento.
 * URL semântica: usa findByNonConformanceIdAndDocumentId (não o UUID interno do link).
 * Se o link não existe → 404 com mensagem clara.
 */
@Service
@Transactional
public class UnlinkNcFromDocumentUseCase {

    private final NonConformanceRepository nonConformanceRepository;
    private final NcDocumentLinkRepository ncDocumentLinkRepository;
    private final AuditService auditService;

    public UnlinkNcFromDocumentUseCase(NonConformanceRepository nonConformanceRepository,
                                       NcDocumentLinkRepository ncDocumentLinkRepository,
                                       AuditService auditService) {
        this.nonConformanceRepository = nonConformanceRepository;
        this.ncDocumentLinkRepository = ncDocumentLinkRepository;
        this.auditService = auditService;
    }

    public void execute(UUID ncId, UUID documentId, String principal) {
        if (!nonConformanceRepository.existsById(ncId)) {
            throw new NcNotFoundException(ncId);
        }

        NcDocumentLink link = ncDocumentLinkRepository
                .findByNonConformanceIdAndDocumentId(ncId, documentId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Vínculo não encontrado entre NC " + ncId + " e documento " + documentId + "."));

        ncDocumentLinkRepository.delete(link);

        auditService.log(principal, AuditAction.NC_DOCUMENT_UNLINKED, "NcDocumentLink",
                ncId, Map.of(
                        "ncId", ncId.toString(),
                        "documentId", documentId.toString()
                ));
    }
}
