package com.industrialhub.backend.common.infrastructure;

import com.industrialhub.backend.common.domain.AuditAction;
import com.industrialhub.backend.common.domain.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    @Query("""
        SELECT a FROM AuditLog a
        WHERE (:entityType IS NULL OR a.entityType = :entityType)
          AND (:action IS NULL OR a.action = :action)
          AND (:username IS NULL OR a.username = :username)
        ORDER BY a.timestamp DESC
        """)
    Page<AuditLog> findWithFilters(
        @Param("entityType") String entityType,
        @Param("action") AuditAction action,
        @Param("username") String username,
        Pageable pageable
    );

    /** Finds audit logs older than the given cutoff whose username is not yet anonymized. */
    @Query("SELECT a FROM AuditLog a WHERE a.timestamp < :cutoff AND a.username <> '[anonimizado]'")
    List<AuditLog> findEligibleForAnonymization(@Param("cutoff") LocalDateTime cutoff);

    /** Count of audit logs eligible for anonymization (dry-run preview). */
    @Query("SELECT COUNT(a) FROM AuditLog a WHERE a.timestamp < :cutoff AND a.username <> '[anonimizado]'")
    long countEligibleForAnonymization(@Param("cutoff") LocalDateTime cutoff);

    /** Finds audit logs by username for data export. */
    List<AuditLog> findByUsernameOrderByTimestampDesc(String username);

    @Query("""
        SELECT a FROM AuditLog a
        WHERE (:username IS NULL OR a.username = :username)
          AND (:module IS NULL OR a.module = :module)
          AND (:action IS NULL OR CAST(a.action AS string) = :action)
          AND (:from IS NULL OR a.timestamp >= :from)
          AND (:to IS NULL OR a.timestamp <= :to)
        ORDER BY a.timestamp DESC
        """)
    Page<AuditLog> findByFilters(
        @Param("username") String username,
        @Param("module")   String module,
        @Param("action")   String action,
        @Param("from")     LocalDateTime from,
        @Param("to")       LocalDateTime to,
        Pageable pageable
    );

    @org.springframework.data.jpa.repository.Modifying
    @Transactional
    @Query("DELETE FROM AuditLog a WHERE a.timestamp < :cutoff")
    int deleteOlderThan(@Param("cutoff") LocalDateTime cutoff);
}
