package com.industrialhub.backend.qms.audit.infrastructure;

import com.industrialhub.backend.qms.audit.domain.AuditFinding;
import com.industrialhub.backend.qms.audit.domain.FindingType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface AuditFindingRepository extends JpaRepository<AuditFinding, UUID> {

    List<AuditFinding> findByAuditId(UUID auditId);

    long countByAuditId(UUID auditId);

    @Query("""
        SELECT COUNT(f) FROM AuditFinding f
        WHERE f.type = 'NON_CONFORMANCE'
        AND (f.linkedNcId IS NULL OR EXISTS (
            SELECT nc FROM NonConformance nc WHERE nc.id = f.linkedNcId AND nc.status <> 'CLOSED'
        ))
        """)
    long countOpenNonConformanceFindings();

    @Query("SELECT COUNT(f) FROM AuditFinding f WHERE f.type = :type")
    long countByType(@Param("type") FindingType type);
}
