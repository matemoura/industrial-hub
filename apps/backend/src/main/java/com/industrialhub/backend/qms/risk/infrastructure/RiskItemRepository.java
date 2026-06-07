package com.industrialhub.backend.qms.risk.infrastructure;

import com.industrialhub.backend.qms.risk.domain.RiskItem;
import com.industrialhub.backend.qms.risk.domain.RiskLevel;
import com.industrialhub.backend.qms.risk.domain.RiskStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface RiskItemRepository extends JpaRepository<RiskItem, UUID> {

    @Query("""
        SELECT r FROM RiskItem r
        WHERE (:status IS NULL OR r.status = :status)
        AND (:riskLevel IS NULL OR r.riskLevel = :riskLevel)
        AND (:owner IS NULL OR LOWER(r.owner) LIKE LOWER(CONCAT('%', :owner, '%')))
        AND (:linkedNcId IS NULL OR r.linkedNcId = :linkedNcId)
        AND (:linkedProductCode IS NULL OR LOWER(r.linkedProductCode) LIKE LOWER(CONCAT('%', :linkedProductCode, '%')))
        ORDER BY r.rpn DESC
        """)
    Page<RiskItem> findByFilters(
        @Param("status") RiskStatus status,
        @Param("riskLevel") RiskLevel riskLevel,
        @Param("owner") String owner,
        @Param("linkedNcId") UUID linkedNcId,
        @Param("linkedProductCode") String linkedProductCode,
        Pageable pageable
    );

    long countByRiskLevel(RiskLevel level);

    long countByStatus(RiskStatus status);

    List<RiskItem> findByLinkedNcId(UUID ncId);

    List<RiskItem> findTop5ByStatusInOrderByRpnDesc(List<RiskStatus> statuses);

    @Query("SELECT AVG(r.rpn) FROM RiskItem r")
    Double findAvgRpn();

    @Query("""
        SELECT r.severity, r.occurrence, COUNT(r), r.riskLevel
        FROM RiskItem r
        WHERE r.status IN ('IDENTIFIED', 'BEING_MITIGATED')
        GROUP BY r.severity, r.occurrence, r.riskLevel
        """)
    List<Object[]> findMatrixData();
}
