package com.industrialhub.backend.qms.ged.infrastructure;

import com.industrialhub.backend.qms.ged.domain.Document;
import com.industrialhub.backend.qms.ged.domain.DocumentCategory;
import com.industrialhub.backend.qms.ged.domain.DocumentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface DocumentRepository extends JpaRepository<Document, UUID> {
    Page<Document> findByCategoryAndStatus(DocumentCategory category, DocumentStatus status, Pageable pageable);
    Page<Document> findByCategory(DocumentCategory category, Pageable pageable);
    Page<Document> findByStatus(DocumentStatus status, Pageable pageable);
    boolean existsByCode(String code);

    /**
     * Sprint 39 / US-117: contagem de documentos por categoria (para relatório executivo).
     */
    @Query("SELECT d.category AS category, COUNT(d) AS cnt FROM Document d GROUP BY d.category")
    List<Object[]> countByCategory();

    /**
     * Sprint 39 / US-117: contagem de documentos por status (para relatório executivo).
     */
    @Query("SELECT d.status AS status, COUNT(d) AS cnt FROM Document d GROUP BY d.status")
    List<Object[]> countByStatus();
}
