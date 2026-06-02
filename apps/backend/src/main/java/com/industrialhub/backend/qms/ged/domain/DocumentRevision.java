package com.industrialhub.backend.qms.ged.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "ged_document_revision", indexes = {
    @Index(name = "idx_doc_rev_document_id", columnList = "document_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentRevision {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false, updatable = false)
    private Document document;

    @Column(nullable = false, updatable = false, length = 10)
    private String revisionNumber;

    @Column(nullable = false, updatable = false, length = 500)
    private String storagePath;

    @Column(nullable = false, updatable = false, length = 255)
    private String originalFileName;

    @Column(nullable = false, updatable = false)
    private Long fileSizeBytes;

    @Column(nullable = false, updatable = false, length = 100)
    private String uploadedBy;

    @Column(nullable = false, updatable = false)
    private LocalDateTime uploadedAt;

    @Column(nullable = false, updatable = false, length = 1000)
    private String changeReason;
}
