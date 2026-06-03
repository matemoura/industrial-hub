package com.industrialhub.backend.qms.infrastructure;

import com.industrialhub.backend.qms.application.dto.CAPASummaryResponse;
import com.industrialhub.backend.qms.domain.ActionStatus;
import com.industrialhub.backend.qms.domain.ActionType;
import com.industrialhub.backend.qms.domain.CorrectiveAction;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
}
