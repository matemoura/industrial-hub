package com.industrialhub.backend.qms.audit.infrastructure;

import com.industrialhub.backend.qms.audit.domain.AuditChecklistItem;
import com.industrialhub.backend.qms.audit.domain.ChecklistResponse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface AuditChecklistItemRepository extends JpaRepository<AuditChecklistItem, UUID> {

    List<AuditChecklistItem> findByAuditIdOrderByItemOrder(UUID auditId);

    long countByAuditId(UUID auditId);

    long countByAuditIdAndResponse(UUID auditId, ChecklistResponse response);

    @Query("""
        SELECT COUNT(i) FROM AuditChecklistItem i
        WHERE i.audit.plannedDate >= :from
        AND i.response IS NOT NULL
        AND i.response <> 'NOT_APPLICABLE'
        """)
    long countRespondedSince(@Param("from") LocalDate from);

    @Query("""
        SELECT COUNT(i) FROM AuditChecklistItem i
        WHERE i.audit.plannedDate >= :from
        AND i.response = 'CONFORMING'
        """)
    long countConformingSince(@Param("from") LocalDate from);
}
