package com.industrialhub.backend.qms.ged.application.usecase;

import com.industrialhub.backend.common.infrastructure.StorageService;
import com.industrialhub.backend.qms.ged.application.dto.DownloadUrlResponse;
import com.industrialhub.backend.qms.ged.domain.DocumentNotFoundException;
import com.industrialhub.backend.qms.ged.domain.DocumentRevision;
import com.industrialhub.backend.qms.ged.infrastructure.DocumentRepository;
import com.industrialhub.backend.qms.ged.infrastructure.DocumentRevisionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.UUID;

@Service
public class GedGetDownloadUrlUseCase {

    private final DocumentRepository documentRepository;
    private final DocumentRevisionRepository revisionRepository;
    private final StorageService storageService;

    public GedGetDownloadUrlUseCase(DocumentRepository documentRepository,
                                    DocumentRevisionRepository revisionRepository,
                                    StorageService storageService) {
        this.documentRepository = documentRepository;
        this.revisionRepository = revisionRepository;
        this.storageService = storageService;
    }

    @Transactional(readOnly = true)
    public DownloadUrlResponse execute(UUID documentId, UUID revisionId) {
        if (!documentRepository.existsById(documentId)) {
            throw new DocumentNotFoundException(documentId);
        }

        DocumentRevision revision = revisionRepository.findById(revisionId)
            .orElseThrow(() -> new IllegalArgumentException("Revisão não encontrada: " + revisionId));

        if (!revision.getDocument().getId().equals(documentId)) {
            throw new IllegalArgumentException("Revisão não pertence ao documento informado");
        }

        String url = storageService.generatePresignedUrl(revision.getStoragePath(), Duration.ofMinutes(15));

        return new DownloadUrlResponse(url, 900L);
    }
}
