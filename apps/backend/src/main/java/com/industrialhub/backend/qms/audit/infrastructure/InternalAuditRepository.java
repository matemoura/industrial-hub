package com.industrialhub.backend.qms.audit.infrastructure;

import com.industrialhub.backend.qms.audit.domain.AuditStatus;
import com.industrialhub.backend.qms.audit.domain.AuditType;
import com.industrialhub.backend.qms.audit.domain.InternalAudit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.UUID;

public interface InternalAuditRepository extends JpaRepository<InternalAudit, UUID> {

    @Query("""
        SELECT a FROM InternalAudit a
        WHERE (:status IS NULL OR a.status = :status)
        AND (:auditType IS NULL OR a.auditType = :auditType)
        AND (:leadAuditor IS NULL OR LOWER(a.leadAuditor) LIKE LOWER(CONCAT('%', :leadAuditor, '%')))
        AND (:from IS NULL OR a.plannedDate >= :from)
        AND (:to IS NULL OR a.plannedDate <= :to)
        ORDER BY a.plannedDate DESC
        """)
    Page<InternalAudit> findByFilters(
        @Param("status") AuditStatus status,
        @Param("auditType") AuditType auditType,
        @Param("leadAuditor") String leadAuditor,
        @Param("from") LocalDate from,
        @Param("to") LocalDate to,
        Pageable pageable
    );

    long countByCodeStartingWith(String prefix);

    @Query("SELECT COUNT(a) FROM InternalAudit a WHERE a.status = :status AND YEAR(a.plannedDate) = :year")
    long countByStatusAndYear(@Param("status") AuditStatus status, @Param("year") int year);

    @Query("SELECT COUNT(a) FROM InternalAudit a WHERE a.status = 'PLANNED' AND a.plannedDate < :today")
    long countOverdue(@Param("today") LocalDate today);

    // US-135 — Management Review aggregations
    @Query("SELECT COUNT(a) FROM InternalAudit a WHERE a.status = 'COMPLETED' AND a.completedDate >= :from AND a.completedDate <= :to")
    long countCompletedBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("SELECT COUNT(a) FROM InternalAudit a WHERE a.plannedDate <= :today AND a.status IN ('PLANNED', 'IN_PROGRESS')")
    long countPlannedNotDone(@Param("today") LocalDate today);
}
