package com.industrialhub.backend.qms.ged.application.usecase;

import com.industrialhub.backend.common.infrastructure.StorageService;
import com.industrialhub.backend.qms.ged.application.dto.CreateDocumentRequest;
import com.industrialhub.backend.qms.ged.application.dto.DocumentResponse;
import com.industrialhub.backend.qms.ged.application.dto.DocumentRevisionResponse;
import com.industrialhub.backend.qms.ged.domain.Document;
import com.industrialhub.backend.qms.ged.domain.DocumentRevision;
import com.industrialhub.backend.qms.ged.domain.DocumentStatus;
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
public class UploadDocumentUseCase {

    private final DocumentRepository documentRepository;
    private final DocumentRevisionRepository revisionRepository;
    private final StorageService storageService;

    public UploadDocumentUseCase(DocumentRepository documentRepository,
                                  DocumentRevisionRepository revisionRepository,
                                  StorageService storageService) {
        this.documentRepository = documentRepository;
        this.revisionRepository = revisionRepository;
        this.storageService = storageService;
    }

    @Transactional
    public DocumentResponse execute(CreateDocumentRequest request, MultipartFile file, String uploadedBy) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Arquivo é obrigatório");
        }

        if (documentRepository.existsByCode(request.code())) {
            throw new IllegalArgumentException("Código de documento já existe: " + request.code());
        }

        String storagePath = String.format("ged/%s/%s_%s",
            request.code(), UUID.randomUUID(), file.getOriginalFilename());

        try {
            storageService.upload(storagePath, file.getInputStream(),
                file.getContentType(), file.getSize());
        } catch (IOException e) {
            throw new IllegalStateException("Erro ao fazer upload do arquivo", e);
        }

        Document document = Document.builder()
            .code(request.code())
            .title(request.title())
            .category(request.category())
            .status(DocumentStatus.DRAFT)
            .createdBy(uploadedBy)
            .createdAt(LocalDateTime.now())
            .build();

        document = documentRepository.save(document);

        DocumentRevision revision = DocumentRevision.builder()
            .document(document)
            .revisionNumber("1.0")
            .storagePath(storagePath)
            .originalFileName(file.getOriginalFilename())
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
}
