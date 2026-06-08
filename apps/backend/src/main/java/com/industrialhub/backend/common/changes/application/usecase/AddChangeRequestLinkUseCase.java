package com.industrialhub.backend.common.changes.application.usecase;

import com.industrialhub.backend.common.changes.application.dto.ChangeRequestLinkRequest;
import com.industrialhub.backend.common.changes.application.dto.ChangeRequestLinkResponse;
import com.industrialhub.backend.common.changes.domain.ChangeEntityType;
import com.industrialhub.backend.common.changes.domain.ChangeRequest;
import com.industrialhub.backend.common.changes.domain.ChangeRequestLink;
import com.industrialhub.backend.common.changes.domain.ChangeRequestNotFoundException;
import com.industrialhub.backend.common.changes.infrastructure.ChangeRequestLinkRepository;
import com.industrialhub.backend.common.changes.infrastructure.ChangeRequestRepository;
import com.industrialhub.backend.qms.ged.domain.DocumentNotFoundException;
import com.industrialhub.backend.qms.ged.infrastructure.DocumentRepository;
import com.industrialhub.backend.qms.domain.NcNotFoundException;
import com.industrialhub.backend.qms.infrastructure.NonConformanceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
public class AddChangeRequestLinkUseCase {

    private final ChangeRequestRepository changeRequestRepository;
    private final ChangeRequestLinkRepository linkRepository;
    private final NonConformanceRepository nonConformanceRepository;
    private final DocumentRepository documentRepository;

    public AddChangeRequestLinkUseCase(ChangeRequestRepository changeRequestRepository,
                                        ChangeRequestLinkRepository linkRepository,
                                        NonConformanceRepository nonConformanceRepository,
                                        DocumentRepository documentRepository) {
        this.changeRequestRepository = changeRequestRepository;
        this.linkRepository = linkRepository;
        this.nonConformanceRepository = nonConformanceRepository;
        this.documentRepository = documentRepository;
    }

    @Transactional
    public ChangeRequestLinkResponse execute(UUID changeRequestId, ChangeRequestLinkRequest req, String principal) {
        ChangeRequest cr = changeRequestRepository.findById(changeRequestId)
            .orElseThrow(() -> new ChangeRequestNotFoundException(changeRequestId));

        // Validate referenced entity exists when applicable
        if (req.entityType() == ChangeEntityType.NON_CONFORMANCE) {
            if (!nonConformanceRepository.existsById(req.entityId())) {
                throw new NcNotFoundException(req.entityId());
            }
        } else if (req.entityType() == ChangeEntityType.DOCUMENT) {
            if (!documentRepository.existsById(req.entityId())) {
                throw new DocumentNotFoundException(req.entityId());
            }
        }
        // EQUIPMENT and RISK_ITEM: referência leve sem validação (sem FK estrutural)

        // Idempotent: return existing link if same entityType+entityId already linked
        Optional<ChangeRequestLink> existing = linkRepository
            .findByChangeRequestIdAndEntityTypeAndEntityId(changeRequestId, req.entityType(), req.entityId());
        if (existing.isPresent()) {
            return ChangeRequestLinkResponse.from(existing.get());
        }

        ChangeRequestLink link = ChangeRequestLink.builder()
            .changeRequest(cr)
            .entityType(req.entityType())
            .entityId(req.entityId())
            .linkNote(req.linkNote())
            .createdBy(principal)
            .build();

        return ChangeRequestLinkResponse.from(linkRepository.save(link));
    }
}
