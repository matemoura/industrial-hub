package com.industrialhub.backend.qms.application.usecase;

import com.industrialhub.backend.qms.application.dto.DocumentNcLinkResponse;
import com.industrialhub.backend.qms.ged.domain.DocumentNotFoundException;
import com.industrialhub.backend.qms.ged.infrastructure.DocumentRepository;
import com.industrialhub.backend.qms.infrastructure.NcDocumentLinkRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Sprint 39 / ADR-050 Decisão 3: lista NCs vinculadas a um documento GED (visão inversa).
 */
@Service
@Transactional(readOnly = true)
public class ListDocumentNonConformancesUseCase {

    private final DocumentRepository documentRepository;
    private final NcDocumentLinkRepository ncDocumentLinkRepository;

    public ListDocumentNonConformancesUseCase(DocumentRepository documentRepository,
                                              NcDocumentLinkRepository ncDocumentLinkRepository) {
        this.documentRepository = documentRepository;
        this.ncDocumentLinkRepository = ncDocumentLinkRepository;
    }

    public List<DocumentNcLinkResponse> execute(UUID documentId) {
        if (!documentRepository.existsById(documentId)) {
            throw new DocumentNotFoundException(documentId);
        }
        return ncDocumentLinkRepository.findByDocumentId(documentId).stream()
                .map(DocumentNcLinkResponse::from)
                .toList();
    }
}
