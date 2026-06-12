package com.industrialhub.backend.common.changes.infrastructure;

import com.industrialhub.backend.common.changes.domain.ChangeRequest;
import com.industrialhub.backend.common.changes.domain.ChangeStatus;
import com.industrialhub.backend.common.changes.domain.ChangeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface ChangeRequestRepository extends JpaRepository<ChangeRequest, UUID> {

    @Query("""
        SELECT cr FROM ChangeRequest cr
        WHERE (:status IS NULL OR cr.status = :status)
        AND (:changeType IS NULL OR cr.changeType = :changeType)
        AND (:requestedBy IS NULL OR LOWER(cr.requestedBy) LIKE LOWER(CONCAT('%', CAST(:requestedBy AS string), '%')))
        AND cr.createdAt >= :from
        AND cr.createdAt <= :to
        ORDER BY cr.createdAt DESC
        """)
    Page<ChangeRequest> findByFilters(
        @Param("status") ChangeStatus status,
        @Param("changeType") ChangeType changeType,
        @Param("requestedBy") String requestedBy,
        @Param("from") LocalDateTime from,
        @Param("to") LocalDateTime to,
        Pageable pageable
    );

    long countByCodeStartingWith(String prefix);

    long countByStatusIn(List<ChangeStatus> statuses);

    long countByRequestedByAndStatusIn(String requestedBy, List<ChangeStatus> statuses);
}
