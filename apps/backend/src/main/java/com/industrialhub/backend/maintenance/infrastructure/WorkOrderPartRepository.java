package com.industrialhub.backend.maintenance.infrastructure;

import com.industrialhub.backend.maintenance.domain.WorkOrderPart;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkOrderPartRepository extends JpaRepository<WorkOrderPart, UUID> {

    @Query("""
        SELECT wop FROM WorkOrderPart wop
        JOIN FETCH wop.sparePart sp
        WHERE wop.workOrder.id = :workOrderId
        ORDER BY wop.addedAt ASC
    """)
    List<WorkOrderPart> findByWorkOrderIdWithPart(@Param("workOrderId") UUID workOrderId);

    @Query("SELECT wop.workOrder.id FROM WorkOrderPart wop WHERE wop.id = :partId")
    Optional<UUID> findWorkOrderIdByPartId(@Param("partId") UUID partId);
}
