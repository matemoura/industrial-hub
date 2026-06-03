package com.industrialhub.backend.qms.ged.application.usecase;

import com.industrialhub.backend.common.infrastructure.StorageService;
import com.industrialhub.backend.qms.ged.application.dto.CreateDocumentRequest;
import com.industrialhub.backend.qms.ged.application.dto.DocumentResponse;
import com.industrialhub.backend.qms.ged.application.dto.DocumentRevisionResponse;
import com.industrialhub.backend.qms.ged.domain.Document;
import com.industrialhub.backend.qms.ged.domain.DocumentCodeAlreadyExistsException;
import com.industrialhub.backend.qms.ged.domain.DocumentRevision;
import com.industrialhub.backend.qms.ged.domain.DocumentStatus;
import com.industrialhub.backend.qms.ged.infrastructure.DocumentRepository;
import com.industrialhub.backend.qms.ged.infrastructure.DocumentRevisionRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class UploadDocumentUseCase {

    private final DocumentRepository documentRepository;
    private final DocumentRevisionRepository revisionRepository;
    private final StorageService storageService;
    private final GedFileValidator gedFileValidator;

    public UploadDocumentUseCase(DocumentRepository documentRepository,
                                  DocumentRevisionRepository revisionRepository,
                                  StorageService storageService,
                                  GedFileValidator gedFileValidator) {
        this.documentRepository = documentRepository;
        this.revisionRepository = revisionRepository;
        this.storageService = storageService;
        this.gedFileValidator = gedFileValidator;
    }

    @Transactional
    public DocumentResponse execute(CreateDocumentRequest request, MultipartFile file, String uploadedBy) {
        // SEC-125: validate MIME type via Tika magic bytes
        gedFileValidator.validate(file);

        // SEC-129: early check (best-effort; race condition handled below by DataIntegrityViolationException)
        if (documentRepository.existsByCode(request.code())) {
            throw new DocumentCodeAlreadyExistsException(request.code());
        }

        // SEC-126: sanitize originalFilename — strip path components to prevent traversal
        String safeFilename = sanitizeFilename(file.getOriginalFilename());

        String storagePath = String.format("ged/%s/%s_%s",
            request.code(), UUID.randomUUID(), safeFilename);

        try {
            storageService.upload(storagePath, file.getInputStream(),
                file.getContentType(), file.getSize());
        } catch (IOException e) {
            throw new IllegalStateException("Erro ao fazer upload do arquivo.", e);
        }

        Document document = Document.builder()
            .code(request.code())
            .title(request.title())
            .category(request.category())
            .status(DocumentStatus.DRAFT)
            .createdBy(uploadedBy)
            .createdAt(LocalDateTime.now())
            .build();

        // SEC-129: catch race condition — two concurrent requests passed existsByCode
        try {
            document = documentRepository.save(document);
        } catch (DataIntegrityViolationException e) {
            throw new DocumentCodeAlreadyExistsException(request.code());
        }

        DocumentRevision revision = DocumentRevision.builder()
            .document(document)
            .revisionNumber("1.0")
            .storagePath(storagePath)
            .originalFileName(safeFilename)
            .fileSizeBytes(file.getSize())
            .uploadedBy(uploadedBy)
            .uploadedAt(LocalDateTime.now())
            .changeReason(request.changeReason())
            .build();

        revision = revisionRepository.save(revision);

        document.setCurrentRevision(revision);
        document = documentRepository.save(document);

        DocumentRevisionResponse revisionResponse = DocumentRevisionResponse.from(revision);

        return new DocumentResponse(
            document.getId(),
            document.getCode(),
            document.getTitle(),
            document.getCategory(),
            document.getStatus(),
            document.getCreatedBy(),
            document.getCreatedAt(),
            revisionResponse,
            List.of(revisionResponse)
        );
    }

    /**
     * SEC-126: Extracts only the filename component, stripping any path separators.
     * "../../etc/passwd.pdf" → "passwd.pdf"
     */
    public static String sanitizeFilename(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return "file";
        }
        try {
            String name = Optional.ofNullable(Paths.get(originalFilename).getFileName())
                .map(Object::toString)
                .orElse("file");
            return name.isBlank() ? "file" : name;
        } catch (Exception e) {
            return "file";
        }
    }
}
