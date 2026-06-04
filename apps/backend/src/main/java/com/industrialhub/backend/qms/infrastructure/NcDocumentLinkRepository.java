package com.industrialhub.backend.qms.infrastructure;

import com.industrialhub.backend.qms.domain.NcDocumentLink;
import com.industrialhub.backend.qms.infrastructure.projection.DocumentNcLinkSummary;
import com.industrialhub.backend.qms.infrastructure.projection.NcDocumentLinkSummary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Sprint 39 / ADR-050 Decisão 2: repositório NC↔GED Link.
 * Projeções de interface para evitar carregamento de entidade completa (sem N+1).
 */
public interface NcDocumentLinkRepository extends JpaRepository<NcDocumentLink, UUID> {

    @Query("""
        SELECT
            l.id          AS linkId,
            d.id          AS documentId,
            d.code        AS documentCode,
            d.title       AS documentTitle,
            d.category    AS documentCategory,
            d.status      AS documentStatus,
            l.linkType    AS linkType,
            l.linkedAt    AS linkedAt
        FROM NcDocumentLink l
        JOIN l.document d
        WHERE l.nonConformance.id = :ncId
        ORDER BY l.linkedAt DESC
    """)
    List<NcDocumentLinkSummary> findByNcId(@Param("ncId") UUID ncId);

    @Query("""
        SELECT
            l.id              AS linkId,
            nc.id             AS ncId,
            nc.title          AS ncTitle,
            nc.severity       AS ncSeverity,
            nc.status         AS ncStatus,
            l.linkType        AS linkType,
            l.linkedAt        AS linkedAt
        FROM NcDocumentLink l
        JOIN l.nonConformance nc
        WHERE l.document.id = :documentId
        ORDER BY l.linkedAt DESC
    """)
    List<DocumentNcLinkSummary> findByDocumentId(@Param("documentId") UUID documentId);

    Optional<NcDocumentLink> findByNonConformanceIdAndDocumentId(UUID ncId, UUID documentId);

    boolean existsByNonConformanceIdAndDocumentId(UUID ncId, UUID documentId);
}
