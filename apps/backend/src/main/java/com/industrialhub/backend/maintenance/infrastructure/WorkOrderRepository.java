package com.industrialhub.backend.maintenance.infrastructure;

import com.industrialhub.backend.maintenance.domain.WorkOrder;
import com.industrialhub.backend.maintenance.domain.WorkOrderPriority;
import com.industrialhub.backend.maintenance.domain.WorkOrderStatus;
import com.industrialhub.backend.maintenance.domain.WorkOrderType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface WorkOrderRepository extends JpaRepository<WorkOrder, UUID> {

    @EntityGraph(attributePaths = {"equipment"})
    @Query("""
        SELECT w FROM WorkOrder w
        WHERE (:equipmentId IS NULL OR w.equipment.id = :equipmentId)
          AND (:type IS NULL OR w.type = :type)
          AND (:status IS NULL OR w.status = :status)
          AND (:priority IS NULL OR w.priority = :priority)
          AND (:slaBreached IS NULL OR w.slaBreached = :slaBreached)
    """)
    Page<WorkOrder> findWithFilters(
        @Param("equipmentId") UUID equipmentId,
        @Param("type") WorkOrderType type,
        @Param("status") WorkOrderStatus status,
        @Param("priority") WorkOrderPriority priority,
        @Param("slaBreached") Boolean slaBreached,
        Pageable pageable
    );

    @EntityGraph(attributePaths = {"equipment"})
    @Query("""
        SELECT w FROM WorkOrder w
        WHERE (:equipmentId IS NULL OR w.equipment.id = :equipmentId)
          AND (:type IS NULL OR w.type = :type)
          AND (:status IS NULL OR w.status = :status)
          AND (:priority IS NULL OR w.priority = :priority)
          AND (:shiftId IS NULL OR w.shift.id = :shiftId)
          AND (:slaBreached IS NULL OR w.slaBreached = :slaBreached)
    """)
    Page<WorkOrder> findWithFiltersAndShift(
        @Param("equipmentId") UUID equipmentId,
        @Param("type") WorkOrderType type,
        @Param("status") WorkOrderStatus status,
        @Param("priority") WorkOrderPriority priority,
        @Param("shiftId") UUID shiftId,
        @Param("slaBreached") Boolean slaBreached,
        Pageable pageable
    );

    @Query("""
        SELECT w FROM WorkOrder w
        WHERE w.status NOT IN (
                com.industrialhub.backend.maintenance.domain.WorkOrderStatus.DONE,
                com.industrialhub.backend.maintenance.domain.WorkOrderStatus.CANCELLED)
          AND w.slaBreached = false
          AND w.openedAt <= :deadline
        """)
    List<WorkOrder> findBreachCandidates(
        @Param("deadline") LocalDateTime deadline
    );

    @Query("SELECT COUNT(w) FROM WorkOrder w WHERE w.slaBreached = true")
    long countBreached();

    @Query("""
        SELECT COUNT(w) FROM WorkOrder w
        WHERE w.status NOT IN (
            com.industrialhub.backend.maintenance.domain.WorkOrderStatus.DONE,
            com.industrialhub.backend.maintenance.domain.WorkOrderStatus.CANCELLED)
    """)
    long countOpen();

    boolean existsByEquipmentIdAndStatusIn(UUID equipmentId, List<WorkOrderStatus> statuses);

    @Query("""
        SELECT w FROM WorkOrder w
        WHERE w.equipment.id = :equipmentId
          AND w.type = com.industrialhub.backend.maintenance.domain.WorkOrderType.CORRECTIVE
          AND w.status IN (com.industrialhub.backend.maintenance.domain.WorkOrderStatus.OPEN,
                           com.industrialhub.backend.maintenance.domain.WorkOrderStatus.IN_PROGRESS)
    """)
    List<WorkOrder> findOpenCorrectiveByEquipmentId(@Param("equipmentId") UUID equipmentId);

    @Query("""
        SELECT w FROM WorkOrder w
        WHERE w.type = com.industrialhub.backend.maintenance.domain.WorkOrderType.CORRECTIVE
          AND w.status = com.industrialhub.backend.maintenance.domain.WorkOrderStatus.DONE
          AND w.startedAt IS NOT NULL
          AND (:equipmentId IS NULL OR w.equipment.id = :equipmentId)
    """)
    List<WorkOrder> findCompletedCorrectiveForMetrics(@Param("equipmentId") UUID equipmentId);

    @Query("""
        SELECT COUNT(w) FROM WorkOrder w
        WHERE (:equipmentId IS NULL OR w.equipment.id = :equipmentId)
    """)
    long countByEquipmentId(@Param("equipmentId") UUID equipmentId);

    @Query("""
        SELECT COUNT(w) FROM WorkOrder w
        WHERE w.status IN (com.industrialhub.backend.maintenance.domain.WorkOrderStatus.OPEN,
                           com.industrialhub.backend.maintenance.domain.WorkOrderStatus.IN_PROGRESS)
          AND (:equipmentId IS NULL OR w.equipment.id = :equipmentId)
    """)
    long countOpenByEquipmentId(@Param("equipmentId") UUID equipmentId);

    @Query("""
        SELECT w FROM WorkOrder w
        WHERE w.type = com.industrialhub.backend.maintenance.domain.WorkOrderType.CORRECTIVE
          AND w.status = com.industrialhub.backend.maintenance.domain.WorkOrderStatus.DONE
          AND w.startedAt IS NOT NULL
          AND w.closedAt >= :from AND w.closedAt < :to
    """)
    List<WorkOrder> findCompletedCorrectiveInPeriod(
        @Param("from") LocalDateTime from,
        @Param("to")   LocalDateTime to
    );

    @Query("""
        SELECT w FROM WorkOrder w
        WHERE w.type = com.industrialhub.backend.maintenance.domain.WorkOrderType.CORRECTIVE
          AND w.status = com.industrialhub.backend.maintenance.domain.WorkOrderStatus.DONE
          AND w.startedAt IS NOT NULL
    """)
    List<WorkOrder> findAllCompletedCorrectiveForMttr();

    @Query("SELECT wo.status, COUNT(wo) FROM WorkOrder wo GROUP BY wo.status")
    List<Object[]> countByStatus();

    @Query("SELECT wo.type, COUNT(wo) FROM WorkOrder wo GROUP BY wo.type")
    List<Object[]> countByType();

    @Query("""
        SELECT wo.status, COUNT(wo) FROM WorkOrder wo
        WHERE wo.shift.id = :shiftId
        GROUP BY wo.status
    """)
    List<Object[]> countByStatusAndShift(@Param("shiftId") UUID shiftId);

    @Query("""
        SELECT wo.type, COUNT(wo) FROM WorkOrder wo
        WHERE wo.shift.id = :shiftId
        GROUP BY wo.type
    """)
    List<Object[]> countByTypeAndShift(@Param("shiftId") UUID shiftId);

    @Query("""
        SELECT COUNT(w) FROM WorkOrder w
        WHERE w.priority = :priority
          AND w.status IN :statuses
          AND w.openedAt < :cutoff
    """)
    long countUrgentOpenOlderThan(
        @Param("priority") WorkOrderPriority priority,
        @Param("statuses") List<WorkOrderStatus> statuses,
        @Param("cutoff") LocalDateTime cutoff);

    @EntityGraph(attributePaths = {"equipment"})
    @Query("""
        SELECT w FROM WorkOrder w
        WHERE (:equipmentId IS NULL OR w.equipment.id = :equipmentId)
          AND (:type IS NULL OR w.type = :type)
          AND (:status IS NULL OR w.status = :status)
          AND (:priority IS NULL OR w.priority = :priority)
          AND (:slaBreached IS NULL OR w.slaBreached = :slaBreached)
          AND w.equipment.plant.id IN :plantIds
    """)
    Page<WorkOrder> findWithFiltersAndPlantIds(
        @Param("equipmentId") UUID equipmentId,
        @Param("type") WorkOrderType type,
        @Param("status") WorkOrderStatus status,
        @Param("priority") WorkOrderPriority priority,
        @Param("slaBreached") Boolean slaBreached,
        @Param("plantIds") List<UUID> plantIds,
        Pageable pageable
    );
}
