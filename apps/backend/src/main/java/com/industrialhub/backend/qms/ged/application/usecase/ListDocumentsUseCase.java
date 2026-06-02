package com.industrialhub.backend.qms.ged.application.usecase;

import com.industrialhub.backend.qms.ged.application.dto.DocumentSummaryResponse;
import com.industrialhub.backend.qms.ged.domain.Document;
import com.industrialhub.backend.qms.ged.domain.DocumentCategory;
import com.industrialhub.backend.qms.ged.domain.DocumentStatus;
import com.industrialhub.backend.qms.ged.infrastructure.DocumentRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class ListDocumentsUseCase {

    private final DocumentRepository documentRepository;

    public ListDocumentsUseCase(DocumentRepository documentRepository) {
        this.documentRepository = documentRepository;
    }

    @Transactional(readOnly = true)
    public Page<DocumentSummaryResponse> execute(DocumentCategory category, DocumentStatus status, Pageable pageable) {
        Page<Document> page;

        if (category != null && status != null) {
            page = documentRepository.findByCategoryAndStatus(category, status, pageable);
        } else if (category != null) {
            page = documentRepository.findByCategory(category, pageable);
        } else if (status != null) {
            page = documentRepository.findByStatus(status, pageable);
        } else {
            page = documentRepository.findAll(pageable);
        }

        return page.map(this::toSummary);
    }

    private DocumentSummaryResponse toSummary(Document document) {
        String currentRevisionNumber = document.getCurrentRevision() != null
            ? document.getCurrentRevision().getRevisionNumber()
            : null;

        LocalDateTime updatedAt = document.getCurrentRevision() != null
            ? document.getCurrentRevision().getUploadedAt()
            : document.getCreatedAt();

        return new DocumentSummaryResponse(
            document.getId(),
            document.getCode(),
            document.getTitle(),
            document.getCategory(),
            document.getStatus(),
            currentRevisionNumber,
            updatedAt
        );
    }
}
