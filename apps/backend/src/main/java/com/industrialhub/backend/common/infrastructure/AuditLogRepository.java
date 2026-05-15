package com.industrialhub.backend.common.infrastructure;

import com.industrialhub.backend.common.domain.AuditAction;
import com.industrialhub.backend.common.domain.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
}
