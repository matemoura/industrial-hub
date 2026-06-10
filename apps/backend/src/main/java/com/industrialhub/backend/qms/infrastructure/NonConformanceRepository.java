package com.industrialhub.backend.qms.infrastructure;

import com.industrialhub.backend.qms.domain.NonConformance;
import com.industrialhub.backend.qms.domain.NcSeverity;
import com.industrialhub.backend.qms.domain.NcStatus;
import com.industrialhub.backend.qms.domain.NcType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;



public interface NonConformanceRepository extends JpaRepository<NonConformance, UUID> {

    @EntityGraph(attributePaths = {"actions"})
    Optional<NonConformance> findWithActionsById(UUID id);

    @EntityGraph(attributePaths = {"actions", "rca"})
    Optional<NonConformance> findWithActionsAndRcaById(UUID id);

    @Query("""
        SELECT nc FROM NonConformance nc
        WHERE (:status IS NULL OR nc.status = :status)
        AND (:severity IS NULL OR nc.severity = :severity)
        AND (:type IS NULL OR nc.type = :type)
        AND (:slaBreached IS NULL OR nc.slaBreached = :slaBreached)
        """)
    Page<NonConformance> findAllFiltered(
        @Param("status") NcStatus status,
        @Param("severity") NcSeverity severity,
        @Param("type") NcType type,
        @Param("slaBreached") Boolean slaBreached,
        Pageable pageable
    );

    @Query("""
        SELECT nc FROM NonConformance nc
        WHERE nc.status <> 'CLOSED'
          AND nc.slaBreached = false
          AND nc.reportedAt <= :deadline
        """)
    List<NonConformance> findBreachCandidates(
        @Param("deadline") LocalDateTime deadline
    );

    @Query("SELECT COUNT(nc) FROM NonConformance nc WHERE nc.slaBreached = true")
    long countBreached();

    @Query("SELECT COUNT(nc) FROM NonConformance nc WHERE nc.status <> 'CLOSED'")
    long countOpen();

    List<NonConformance> findAllByOrderByReportedAtDesc();

    @Query("SELECT COUNT(nc) FROM NonConformance nc WHERE nc.status = :status")
    long countByStatus(@Param("status") NcStatus status);

    @Query("SELECT COUNT(nc) FROM NonConformance nc WHERE nc.severity = :severity")
    long countBySeverity(@Param("severity") NcSeverity severity);

    @Query("SELECT COUNT(nc) FROM NonConformance nc WHERE nc.reportedAt >= :start AND nc.reportedAt < :end")
    long countInPeriod(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT nc FROM NonConformance nc WHERE nc.supplier.id = :supplierId AND nc.reportedAt >= :from")
    List<NonConformance> findBySupplierIdAndReportedAtAfter(@Param("supplierId") UUID supplierId,
                                                            @Param("from") LocalDateTime from);

    @Query("SELECT nc FROM NonConformance nc WHERE nc.type = :type AND nc.reportedAt >= :from")
    List<NonConformance> findByTypeAndReportedAtAfter(@Param("type") NcType type,
                                                      @Param("from") LocalDateTime from);

    @Query("SELECT nc FROM NonConformance nc WHERE nc.reportedAt >= :from")
    List<NonConformance> findAllCreatedAfter(@Param("from") LocalDateTime from);

    @Query("SELECT nc FROM NonConformance nc WHERE nc.reportedAt >= :from AND nc.reportedAt < :to")
    List<NonConformance> findAllCreatedBetween(@Param("from") LocalDateTime from,
                                               @Param("to") LocalDateTime to);

    @Query("SELECT COUNT(nc) FROM NonConformance nc WHERE nc.severity = :severity AND nc.status = :status")
    long countBySeverityAndStatus(@Param("severity") NcSeverity severity, @Param("status") NcStatus status);

    /** Finds NCs older than cutoff whose reportedBy is not yet anonymized (for retention job). */
    @Query("SELECT nc FROM NonConformance nc WHERE nc.reportedAt < :cutoff AND nc.reportedBy <> '[anonimizado]'")
    List<NonConformance> findEligibleForAnonymization(@Param("cutoff") LocalDateTime cutoff);

    /** Count of NCs eligible for anonymization (dry-run preview). */
    @Query("SELECT COUNT(nc) FROM NonConformance nc WHERE nc.reportedAt < :cutoff AND nc.reportedBy <> '[anonimizado]'")
    long countEligibleForAnonymization(@Param("cutoff") LocalDateTime cutoff);

    /** Finds NCs reported by a specific username (for data export and individual anonymization). */
    List<NonConformance> findByReportedByOrderByReportedAtDesc(String reportedBy);

    // US-135 — Management Review aggregations
    @Query("SELECT COUNT(nc) FROM NonConformance nc WHERE nc.severity = 'CRITICAL' AND nc.status <> 'CLOSED'")
    long countCriticalOpen();

    @Query("SELECT nc FROM NonConformance nc WHERE nc.status = 'CLOSED' AND nc.closedAt >= :from AND nc.closedAt < :to")
    List<NonConformance> findClosedBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("""
        SELECT nc FROM NonConformance nc
        WHERE (:status IS NULL OR nc.status = :status)
        AND (:severity IS NULL OR nc.severity = :severity)
        AND (:type IS NULL OR nc.type = :type)
        AND (:slaBreached IS NULL OR nc.slaBreached = :slaBreached)
        AND nc.plant.id IN :plantIds
        """)
    Page<NonConformance> findAllFilteredByPlantIds(
        @Param("status") NcStatus status,
        @Param("severity") NcSeverity severity,
        @Param("type") NcType type,
        @Param("slaBreached") Boolean slaBreached,
        @Param("plantIds") Collection<UUID> plantIds,
        Pageable pageable
    );
}
