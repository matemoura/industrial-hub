package com.industrialhub.backend.qms.domain;

import com.industrialhub.backend.qms.ged.domain.Document;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Sprint 39 / ADR-050 Decisão 1: entidade de junção NC↔Documento.
 * Pertence ao domínio QMS principal (não a qms/ged/domain/) pois representa
 * o cruzamento entre NonConformance (qms/) e Document (qms/ged/).
 * Vínculo é imutável após criação — campos de identidade com updatable=false.
 * linkedBy é retido para auditoria mas NÃO exposto via API (ADR-049 §4).
 */
@Entity
@Table(
    name = "nc_document_link",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_nc_document_link",
            columnNames = {"non_conformance_id", "document_id"}
        )
    },
    indexes = {
        @Index(name = "idx_nc_doc_link_nc",  columnList = "non_conformance_id"),
        @Index(name = "idx_nc_doc_link_doc", columnList = "document_id")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NcDocumentLink {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "non_conformance_id", nullable = false, updatable = false)
    private NonConformance nonConformance;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false, updatable = false)
    private Document document;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30, updatable = false)
    private NcDocumentLinkType linkType;

    @Column(nullable = false, updatable = false, length = 100)
    private String linkedBy;

    @Column(nullable = false, updatable = false)
    private LocalDateTime linkedAt;

    public NcDocumentLink(NonConformance nonConformance, Document document,
                          NcDocumentLinkType linkType, String linkedBy) {
        this.nonConformance = nonConformance;
        this.document = document;
        this.linkType = linkType;
        this.linkedBy = linkedBy;
        this.linkedAt = LocalDateTime.now();
    }
}
