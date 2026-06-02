package com.industrialhub.backend.qms.ged.application.usecase;

import com.industrialhub.backend.qms.ged.application.dto.DocumentResponse;
import com.industrialhub.backend.qms.ged.application.dto.DocumentRevisionResponse;
import com.industrialhub.backend.qms.ged.domain.Document;
import com.industrialhub.backend.qms.ged.domain.DocumentNotFoundException;
import com.industrialhub.backend.qms.ged.infrastructure.DocumentRepository;
import com.industrialhub.backend.qms.ged.infrastructure.DocumentRevisionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class GetDocumentUseCase {

    private final DocumentRepository documentRepository;
    private final DocumentRevisionRepository revisionRepository;

    public GetDocumentUseCase(DocumentRepository documentRepository,
                               DocumentRevisionRepository revisionRepository) {
        this.documentRepository = documentRepository;
        this.revisionRepository = revisionRepository;
    }

    @Transactional(readOnly = true)
    public DocumentResponse execute(UUID id) {
        Document document = documentRepository.findById(id)
            .orElseThrow(() -> new DocumentNotFoundException(id));

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
