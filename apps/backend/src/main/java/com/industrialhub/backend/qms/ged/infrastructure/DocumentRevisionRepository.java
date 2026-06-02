package com.industrialhub.backend.qms.ged.infrastructure;

import com.industrialhub.backend.qms.ged.domain.DocumentRevision;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface DocumentRevisionRepository extends JpaRepository<DocumentRevision, UUID> {
    List<DocumentRevision> findByDocumentIdOrderByUploadedAtDesc(UUID documentId);

    @Query("SELECT dr.revisionNumber FROM DocumentRevision dr WHERE dr.document.id = :documentId ORDER BY dr.uploadedAt DESC")
    List<String> findRevisionNumbersByDocumentId(@Param("documentId") UUID documentId);
}
