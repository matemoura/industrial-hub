package com.industrialhub.backend.qms.ged.application.usecase;

import com.industrialhub.backend.common.infrastructure.StorageService;
import com.industrialhub.backend.qms.ged.application.dto.DocumentRevisionResponse;
import com.industrialhub.backend.qms.ged.domain.Document;
import com.industrialhub.backend.qms.ged.domain.DocumentNotFoundException;
import com.industrialhub.backend.qms.ged.domain.DocumentRevision;
import com.industrialhub.backend.qms.ged.domain.DocumentStatus;
import com.industrialhub.backend.qms.ged.domain.InvalidGedTransitionException;
import com.industrialhub.backend.qms.ged.infrastructure.DocumentRepository;
import com.industrialhub.backend.qms.ged.infrastructure.DocumentRevisionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class AddRevisionUseCase {

    private final DocumentRepository documentRepository;
    private final DocumentRevisionRepository revisionRepository;
    private final StorageService storageService;

    public AddRevisionUseCase(DocumentRepository documentRepository,
                               DocumentRevisionRepository revisionRepository,
                               StorageService storageService) {
        this.documentRepository = documentRepository;
        this.revisionRepository = revisionRepository;
        this.storageService = storageService;
    }

    @Transactional
    public DocumentRevisionResponse execute(UUID documentId, String changeReason, MultipartFile file, String uploadedBy) {
        Document document = documentRepository.findById(documentId)
            .orElseThrow(() -> new DocumentNotFoundException(documentId));

        if (document.getStatus() == DocumentStatus.OBSOLETE) {
            throw new InvalidGedTransitionException("Documento obsoleto não aceita novas revisões");
        }

        String nextRevisionNumber = calculateNextRevisionNumber(documentId);

        String storagePath = String.format("ged/%s/%s_%s",
            document.getCode(), UUID.randomUUID(), file.getOriginalFilename());

        try {
            storageService.upload(storagePath, file.getInputStream(),
                file.getContentType(), file.getSize());
        } catch (IOException e) {
            throw new IllegalStateException("Erro ao fazer upload do arquivo", e);
        }

        DocumentRevision revision = DocumentRevision.builder()
            .document(document)
            .revisionNumber(nextRevisionNumber)
            .storagePath(storagePath)
            .originalFileName(file.getOriginalFilename())
            .fileSizeBytes(file.getSize())
            .uploadedBy(uploadedBy)
            .uploadedAt(LocalDateTime.now())
            .changeReason(changeReason)
            .build();

        revision = revisionRepository.save(revision);

        document.setCurrentRevision(revision);
        documentRepository.save(document);

        return DocumentRevisionResponse.from(revision);
    }

    private String calculateNextRevisionNumber(UUID documentId) {
        List<String> revisionNumbers = revisionRepository.findRevisionNumbersByDocumentId(documentId);
        if (revisionNumbers.isEmpty()) {
            return "1.0";
        }

        double maxVersion = revisionNumbers.stream()
            .mapToDouble(s -> {
                try {
                    return Double.parseDouble(s);
                } catch (NumberFormatException e) {
                    return 0.0;
                }
            })
            .max()
            .orElse(0.0);

        double next = maxVersion + 1.0;
        // format as "N.0"
        long nextLong = (long) next;
        return nextLong + ".0";
    }
}
