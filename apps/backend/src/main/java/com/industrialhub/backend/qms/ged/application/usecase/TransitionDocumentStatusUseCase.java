package com.industrialhub.backend.qms.ged.application.usecase;

import com.industrialhub.backend.qms.ged.application.dto.DocumentResponse;
import com.industrialhub.backend.qms.ged.application.dto.DocumentRevisionResponse;
import com.industrialhub.backend.qms.ged.domain.Document;
import com.industrialhub.backend.qms.ged.domain.DocumentNotFoundException;
import com.industrialhub.backend.qms.ged.domain.DocumentStatus;
import com.industrialhub.backend.qms.ged.domain.InvalidGedTransitionException;
import com.industrialhub.backend.qms.ged.infrastructure.DocumentRepository;
import com.industrialhub.backend.qms.ged.infrastructure.DocumentRevisionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class TransitionDocumentStatusUseCase {

    private final DocumentRepository documentRepository;
    private final DocumentRevisionRepository revisionRepository;

    public TransitionDocumentStatusUseCase(DocumentRepository documentRepository,
                                            DocumentRevisionRepository revisionRepository) {
        this.documentRepository = documentRepository;
        this.revisionRepository = revisionRepository;
    }

    @Transactional
    public DocumentResponse execute(UUID id, DocumentStatus newStatus) {
        Document document = documentRepository.findById(id)
            .orElseThrow(() -> new DocumentNotFoundException(id));

        DocumentStatus currentStatus = document.getStatus();

        switch (currentStatus) {
            case DRAFT -> {
                if (newStatus == DocumentStatus.PUBLISHED) {
                    if (document.getCurrentRevision() == null) {
                        throw new InvalidGedTransitionException("Documento sem revisão não pode ser publicado");
                    }
                } else {
                    throw new InvalidGedTransitionException(currentStatus, newStatus);
                }
            }
            case PUBLISHED -> {
                if (newStatus != DocumentStatus.OBSOLETE) {
                    throw new InvalidGedTransitionException(currentStatus, newStatus);
                }
            }
            case OBSOLETE -> throw new InvalidGedTransitionException(currentStatus, newStatus);
        }

        document.setStatus(newStatus);
        document = documentRepository.save(document);

        DocumentRevisionResponse currentRevisionResponse = document.getCurrentRevision() != null
            ? DocumentRevisionResponse.from(document.getCurrentRevision())
            : null;

        List<DocumentRevisionResponse> revisions = revisionRepository
            .findByDocumentIdOrderByUploadedAtDesc(id)
            .stream()
            .map(DocumentRevisionResponse::from)
            .collect(Collectors.toList());

        return new DocumentResponse(
            document.getId(),
            document.getCode(),
            document.getTitle(),
            document.getCategory(),
            document.getStatus(),
            document.getCreatedBy(),
            document.getCreatedAt(),
            currentRevisionResponse,
            revisions
        );
    }
}
