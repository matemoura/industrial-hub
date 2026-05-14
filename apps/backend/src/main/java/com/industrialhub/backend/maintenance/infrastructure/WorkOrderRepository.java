package com.industrialhub.backend.maintenance.infrastructure;

import com.industrialhub.backend.maintenance.domain.WorkOrder;
import com.industrialhub.backend.maintenance.domain.WorkOrderPriority;
import com.industrialhub.backend.maintenance.domain.WorkOrderStatus;
import com.industrialhub.backend.maintenance.domain.WorkOrderType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface WorkOrderRepository extends JpaRepository<WorkOrder, UUID> {

    @Query("""
        SELECT w FROM WorkOrder w
        WHERE (:equipmentId IS NULL OR w.equipment.id = :equipmentId)
          AND (:type IS NULL OR w.type = :type)
          AND (:status IS NULL OR w.status = :status)
          AND (:priority IS NULL OR w.priority = :priority)
    """)
    Page<WorkOrder> findWithFilters(
        @Param("equipmentId") UUID equipmentId,
        @Param("type") WorkOrderType type,
        @Param("status") WorkOrderStatus status,
        @Param("priority") WorkOrderPriority priority,
        Pageable pageable
    );

    boolean existsByEquipmentIdAndStatusIn(UUID equipmentId, List<WorkOrderStatus> statuses);

    @Query("""
        SELECT w FROM WorkOrder w
        WHERE w.equipment.id = :equipmentId
          AND w.type = com.industrialhub.backend.maintenance.domain.WorkOrderType.CORRECTIVE
          AND w.status IN (com.industrialhub.backend.maintenance.domain.WorkOrderStatus.OPEN,
                           com.industrialhub.backend.maintenance.domain.WorkOrderStatus.IN_PROGRESS)
    """)
    List<WorkOrder> findOpenCorrectiveByEquipmentId(@Param("equipmentId") UUID equipmentId);
}
