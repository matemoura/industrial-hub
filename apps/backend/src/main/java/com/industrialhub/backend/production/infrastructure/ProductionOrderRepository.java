package com.industrialhub.backend.production.infrastructure;

import com.industrialhub.backend.production.domain.ProductionOrder;
import com.industrialhub.backend.production.domain.ProductionOrderStatus;
import com.industrialhub.backend.production.domain.ProductType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.industrialhub.backend.production.infrastructure.ProductionOrderTrackingView;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductionOrderRepository extends JpaRepository<ProductionOrder, UUID> {

    Optional<ProductionOrder> findByDynamicsOrderNumber(String dynamicsOrderNumber);

    /**
     * Returns tracking view for all non-terminal orders, optionally filtered by family.
     * DONE orders from the current week are included (status = DONE AND importedAt >= weekStart).
     * CANCELLED orders are excluded.
     * ADR-041 Decisão 6.
     */
    @Query("""
        SELECT
            CAST(po.id AS string)             AS id,
            po.dynamicsOrderNumber             AS dynamicsOrderNumber,
            p.name                             AS productName,
            COALESCE(f.name, '')               AS productFamilyName,
            po.status                          AS status,
            po.plannedQty                      AS plannedQty,
            po.producedQty                     AS producedQty,
            po.dueDate                         AS dueDate
        FROM ProductionOrder po
        JOIN po.product p
        LEFT JOIN po.family f
        WHERE po.status <> 'CANCELLED'
          AND (
               po.status <> 'DONE'
               OR (po.status = 'DONE' AND po.importedAt >= :weekStart)
          )
          AND (:familyCode IS NULL OR f.code = :familyCode)
        ORDER BY po.dueDate ASC NULLS LAST
        """)
    List<ProductionOrderTrackingView> findForTracking(
            @Param("familyCode") String familyCode,
            @Param("weekStart") java.time.LocalDateTime weekStart
    );

    @Query("SELECT o FROM ProductionOrder o LEFT JOIN o.product p LEFT JOIN o.family f " +
           "WHERE (:familyCode IS NULL OR f.code = :familyCode) " +
           "AND (:status IS NULL OR o.status = :status) " +
           "AND (:productType IS NULL OR p.type = :productType) " +
           "AND (:overdueOnly = false OR (o.dueDate < :today AND o.status NOT IN ('DONE', 'CANCELLED')))")
    Page<ProductionOrder> findFiltered(
            @Param("familyCode") String familyCode,
            @Param("status") ProductionOrderStatus status,
            @Param("productType") ProductType productType,
            @Param("overdueOnly") boolean overdueOnly,
            @Param("today") LocalDate today,
            Pageable pageable);
}
