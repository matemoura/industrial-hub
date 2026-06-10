package com.industrialhub.backend.qms.infrastructure;

import com.industrialhub.backend.qms.application.dto.CAPASummaryResponse;
import com.industrialhub.backend.qms.domain.ActionStatus;
import com.industrialhub.backend.qms.domain.ActionType;
import com.industrialhub.backend.qms.domain.CorrectiveAction;
import com.industrialhub.backend.qms.infrastructure.projection.CapaAgingProjection;
import com.industrialhub.backend.qms.infrastructure.projection.CapaResolutionProjection;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CorrectiveActionRepository extends JpaRepository<CorrectiveAction, UUID> {

    /**
     * SEC-139: Pessimistic write lock for the effectiveness verification flow.
     * Prevents TOCTOU race condition in auto-close NC logic:
     * concurrent requests both reading hasOpen=false and both saving NC.status=CLOSED.
     * SELECT FOR UPDATE serializes concurrent effectiveness verifications on the same action.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM CorrectiveAction a WHERE a.id = :id")
    Optional<CorrectiveAction> findByIdForUpdate(@Param("id") UUID id);

    List<CorrectiveAction> findByNonConformanceId(UUID ncId);

    boolean existsByNonConformanceIdAndStatus(UUID ncId, ActionStatus status);

    boolean existsByNonConformanceIdAndStatusIn(UUID ncId, List<ActionStatus> statuses);

    @Query("""
        SELECT new com.industrialhub.backend.qms.application.dto.CAPASummaryResponse(
            a.id,
            nc.id,
            nc.title,
            a.description,
            a.type,
            a.status,
            a.responsible,
            a.dueDate,
            a.effectivenessCheckDate
        )
        FROM CorrectiveAction a
        JOIN a.nonConformance nc
        WHERE (:type IS NULL OR a.type = :type)
          AND (:status IS NULL OR a.status = :status)
          AND (:ncId IS NULL OR nc.id = :ncId)
        ORDER BY a.dueDate ASC NULLS LAST
    """)
    Page<CAPASummaryResponse> findAllCapas(
        @Param("type") ActionType type,
        @Param("status") ActionStatus status,
        @Param("ncId") UUID ncId,
        Pageable pageable);

    /**
     * Sprint 39 / ADR-050 Decisão 4: projeção leve para cálculo de aging de CAPAs.
     * Retorna apenas CAPAs abertas (PENDING ou PENDING_EFFECTIVENESS).
     */
    @Query("""
        SELECT
            a.id         AS actionId,
            a.dueDate    AS dueDate,
            a.status     AS status,
            nc.severity  AS ncSeverity
        FROM CorrectiveAction a
        JOIN a.nonConformance nc
        WHERE a.status IN ('PENDING', 'PENDING_EFFECTIVENESS')
    """)
    List<CapaAgingProjection> findOpenCapasForAging();

    /**
     * Sprint 39 / BUG-3: projeção leve de CAPAs concluídas para cálculo de avgResolutionDaysOpen.
     * Retorna apenas registros com createdAt não nulo e completedAt não nulo.
     */
    @Query("""
        SELECT
            a.createdAt   AS createdAt,
            a.completedAt AS completedAt
        FROM CorrectiveAction a
        WHERE a.status = 'DONE'
          AND a.completedAt IS NOT NULL
          AND a.createdAt IS NOT NULL
    """)
    List<CapaResolutionProjection> findDoneCapasForResolutionMetric();

    /**
     * Sprint 39 / US-117: contagem de CAPAs abertas por status (para relatório executivo).
     */
    @Query("""
        SELECT a.status AS status, COUNT(a) AS cnt
        FROM CorrectiveAction a
        GROUP BY a.status
    """)
    List<Object[]> countByStatus();

    /**
     * Sprint 39 / US-117: contagem de CAPAs por tipo (para relatório executivo).
     */
    @Query("""
        SELECT a.type AS type, COUNT(a) AS cnt
        FROM CorrectiveAction a
        GROUP BY a.type
    """)
    List<Object[]> countByType();

    // US-135 — Management Review aggregations
    long countByStatusIn(List<ActionStatus> statuses);

    @Query("SELECT COUNT(a) FROM CorrectiveAction a WHERE a.dueDate < :today AND a.status <> 'DONE'")
    long countOverdue(@Param("today") LocalDate today);
}
