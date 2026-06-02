package com.industrialhub.backend.qms.ged.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "ged_document", indexes = {
    @Index(name = "idx_doc_code",   columnList = "code"),
    @Index(name = "idx_doc_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(nullable = false, length = 200)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DocumentCategory category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DocumentStatus status;

    @OneToOne(fetch = FetchType.LAZY, cascade = CascadeType.MERGE)
    @JoinColumn(name = "current_revision_id", nullable = true)
    private DocumentRevision currentRevision;

    @Column(nullable = false, length = 100)
    private String createdBy;

    @Column(nullable = false)
    private LocalDateTime createdAt;
}
