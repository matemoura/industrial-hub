package com.industrialhub.backend.production.infrastructure;

import com.industrialhub.backend.production.application.dto.PlanningSummaryRow;
import com.industrialhub.backend.production.domain.ProductionOrder;
import com.industrialhub.backend.production.domain.ProductionOrderStatus;
import com.industrialhub.backend.production.domain.ProductType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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

    /** ADR-042 Decisão 2 — bulk-clear sterilizationLoad for REJECTED load */
    @Modifying
    @Query("UPDATE ProductionOrder po SET po.sterilizationLoad = null WHERE po.sterilizationLoad.id = :loadId")
    void clearLoadForAllOrdersInLoad(@Param("loadId") java.util.UUID loadId);

    /** Check if OP is already allocated to an active (non-REJECTED) load */
    @Query("""
        SELECT COUNT(po) > 0
        FROM ProductionOrder po
        WHERE po.id = :orderId
          AND po.sterilizationLoad IS NOT NULL
          AND po.sterilizationLoad.status <> com.industrialhub.backend.production.domain.LoadStatus.REJECTED
        """)
    boolean existsActiveAllocationForOrder(@Param("orderId") java.util.UUID orderId);

    /** Orders allocated to a specific load (for detail view) */
    @Query("""
        SELECT po FROM ProductionOrder po
        JOIN FETCH po.product p
        LEFT JOIN FETCH po.family f
        WHERE po.sterilizationLoad.id = :loadId
        ORDER BY po.dueDate ASC NULLS LAST
        """)
    java.util.List<ProductionOrder> findAllByLoadId(@Param("loadId") java.util.UUID loadId);

    /** US-085 / ADR-043 — soma plannedQty de OPs abertas por produto (para cálculo MRP) */
    @Query("""
        SELECT COALESCE(SUM(CAST(po.plannedQty AS int)), 0)
        FROM ProductionOrder po
        WHERE po.product.id = :productId
          AND po.status IN (
              com.industrialhub.backend.production.domain.ProductionOrderStatus.PLANNED,
              com.industrialhub.backend.production.domain.ProductionOrderStatus.RELEASED,
              com.industrialhub.backend.production.domain.ProductionOrderStatus.IN_PROGRESS
          )
        """)
    int sumOpenOrdersQtyByProduct(@Param("productId") java.util.UUID productId);

    /** US-086 / ADR-043 — OPs abertas de um produto com dueDate para cálculo de staffing */
    @Query("""
        SELECT po FROM ProductionOrder po
        JOIN FETCH po.product p
        WHERE po.product.id = :productId
          AND po.status IN (
              com.industrialhub.backend.production.domain.ProductionOrderStatus.PLANNED,
              com.industrialhub.backend.production.domain.ProductionOrderStatus.RELEASED,
              com.industrialhub.backend.production.domain.ProductionOrderStatus.IN_PROGRESS
          )
        ORDER BY po.dueDate ASC NULLS LAST
        """)
    java.util.List<ProductionOrder> findOpenOrdersByProduct(@Param("productId") java.util.UUID productId);

    /** US-087 — OPs abertas por família para timeline */
    @Query("""
        SELECT po FROM ProductionOrder po
        JOIN FETCH po.product p
        LEFT JOIN FETCH po.family f
        WHERE (:familyCode IS NULL OR f.code = :familyCode)
          AND po.status NOT IN (
              com.industrialhub.backend.production.domain.ProductionOrderStatus.DONE,
              com.industrialhub.backend.production.domain.ProductionOrderStatus.CANCELLED
          )
        ORDER BY po.dueDate ASC NULLS LAST
        """)
    java.util.List<ProductionOrder> findOpenOrdersByFamily(@Param("familyCode") String familyCode);

    /** US-087 — soma plannedQty de OPs abertas por produto (para planning board) */
    @Query("""
        SELECT COALESCE(SUM(CAST(po.plannedQty AS int)), 0)
        FROM ProductionOrder po
        WHERE po.product.id = :productId
          AND po.status NOT IN (
              com.industrialhub.backend.production.domain.ProductionOrderStatus.DONE,
              com.industrialhub.backend.production.domain.ProductionOrderStatus.CANCELLED
          )
        """)
    int sumOpenOrdersQtyForBoardByProduct(@Param("productId") java.util.UUID productId);

    /** US-087 — soma plannedPeople de OPs abertas por produto (para planning board) */
    @Query("""
        SELECT COALESCE(SUM(po.plannedPeople), 0)
        FROM ProductionOrder po
        WHERE po.product.id = :productId
          AND po.status NOT IN (
              com.industrialhub.backend.production.domain.ProductionOrderStatus.DONE,
              com.industrialhub.backend.production.domain.ProductionOrderStatus.CANCELLED
          )
          AND po.plannedPeople IS NOT NULL
        """)
    int sumPlannedPeopleByProduct(@Param("productId") java.util.UUID productId);

    /** US-087 — count e earliest dueDate de OPs abertas por produto */
    @Query("""
        SELECT COUNT(po), MIN(po.dueDate)
        FROM ProductionOrder po
        WHERE po.product.id = :productId
          AND po.status NOT IN (
              com.industrialhub.backend.production.domain.ProductionOrderStatus.DONE,
              com.industrialhub.backend.production.domain.ProductionOrderStatus.CANCELLED
          )
        """)
    Object[] countAndEarliestDueDateByProduct(@Param("productId") java.util.UUID productId);

    /** Pending orders for sterilization queue */
    @Query("""
        SELECT po FROM ProductionOrder po
        JOIN FETCH po.product p
        JOIN FETCH po.family f
        WHERE po.status = com.industrialhub.backend.production.domain.ProductionOrderStatus.DONE
          AND p.requiresSterilization = true
          AND po.sterilizationLoad IS NULL
        ORDER BY po.dueDate ASC NULLS LAST
        """)
    java.util.List<ProductionOrder> findPendingForSterilization();

    /**
     * US-102 / ADR-044 Decisão 5 — aggregation query para relatório planned vs actual.
     * efficiency calculada em Java (não em JPQL) para evitar divisão por zero.
     * pendingMrpQty via subconsulta correlacionada sobre MrpPlannedOrder.
     */
    @Query("""
        SELECT new com.industrialhub.backend.production.application.dto.PlanningSummaryRow(
            f.code,
            f.name,
            p.dynamicsCode,
            p.name,
            CAST(COALESCE(SUM(CAST(po.plannedQty AS int)), 0) AS int),
            CAST(COALESCE(SUM(CASE WHEN po.status = 'DONE' THEN CAST(po.producedQty AS int) ELSE 0 END), 0) AS int),
            CAST(NULL AS java.lang.Double),
            CAST(0 AS int)
        )
        FROM ProductionOrder po
        JOIN po.product p
        JOIN p.family f
        WHERE (:familyCode IS NULL OR f.code = :familyCode)
          AND po.dueDate BETWEEN :from AND :to
          AND po.status <> com.industrialhub.backend.production.domain.ProductionOrderStatus.CANCELLED
        GROUP BY f.code, f.name, p.dynamicsCode, p.name
        ORDER BY f.code, p.dynamicsCode
        """)
    java.util.List<PlanningSummaryRow> findPlanningSummaryRaw(
            @Param("familyCode") String familyCode,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    /**
     * US-104 / ADR-045 Decisão 1 — OPs com status DONE no período (por dueDate).
     * Usada para calcular tendência de eficiência dos últimos 30 dias no painel executivo.
     */
    @Query("SELECT o FROM ProductionOrder o " +
           "WHERE o.status = com.industrialhub.backend.production.domain.ProductionOrderStatus.DONE " +
           "AND o.dueDate >= :from AND o.dueDate <= :to")
    java.util.List<ProductionOrder> findDoneOrdersInPeriod(
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);
}
