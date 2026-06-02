package com.industrialhub.backend.qms.ged.infrastructure;

import com.industrialhub.backend.qms.ged.domain.Document;
import com.industrialhub.backend.qms.ged.domain.DocumentCategory;
import com.industrialhub.backend.qms.ged.domain.DocumentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DocumentRepository extends JpaRepository<Document, UUID> {
    Page<Document> findByCategoryAndStatus(DocumentCategory category, DocumentStatus status, Pageable pageable);
    Page<Document> findByCategory(DocumentCategory category, Pageable pageable);
    Page<Document> findByStatus(DocumentStatus status, Pageable pageable);
    boolean existsByCode(String code);
}
