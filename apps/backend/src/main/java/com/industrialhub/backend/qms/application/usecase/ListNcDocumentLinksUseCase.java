package com.industrialhub.backend.qms.application.usecase;

import com.industrialhub.backend.qms.application.dto.NcDocumentLinkResponse;
import com.industrialhub.backend.qms.domain.NcNotFoundException;
import com.industrialhub.backend.qms.infrastructure.NcDocumentLinkRepository;
import com.industrialhub.backend.qms.infrastructure.NonConformanceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Sprint 39 / ADR-050 Decisão 2: lista documentos vinculados a uma NC.
 */
@Service
@Transactional(readOnly = true)
public class ListNcDocumentLinksUseCase {

    private final NonConformanceRepository nonConformanceRepository;
    private final NcDocumentLinkRepository ncDocumentLinkRepository;

    public ListNcDocumentLinksUseCase(NonConformanceRepository nonConformanceRepository,
                                      NcDocumentLinkRepository ncDocumentLinkRepository) {
        this.nonConformanceRepository = nonConformanceRepository;
        this.ncDocumentLinkRepository = ncDocumentLinkRepository;
    }

    public List<NcDocumentLinkResponse> execute(UUID ncId) {
        if (!nonConformanceRepository.existsById(ncId)) {
            throw new NcNotFoundException(ncId);
        }
        return ncDocumentLinkRepository.findByNcId(ncId).stream()
                .map(NcDocumentLinkResponse::from)
                .toList();
    }
}
