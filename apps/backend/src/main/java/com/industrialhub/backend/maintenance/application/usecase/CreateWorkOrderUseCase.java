package com.industrialhub.backend.maintenance.application.usecase;

import com.industrialhub.backend.common.application.AuditService;
import com.industrialhub.backend.common.domain.AuditAction;
import com.industrialhub.backend.maintenance.application.dto.CreateWorkOrderRequest;
import com.industrialhub.backend.maintenance.application.dto.WorkOrderResponse;
import com.industrialhub.backend.maintenance.domain.Equipment;
import com.industrialhub.backend.maintenance.domain.EquipmentNotFoundException;
import com.industrialhub.backend.maintenance.domain.EquipmentStatus;
import com.industrialhub.backend.maintenance.domain.WorkOrder;
import com.industrialhub.backend.maintenance.domain.WorkOrderStatus;
import com.industrialhub.backend.maintenance.domain.WorkOrderType;
import com.industrialhub.backend.maintenance.infrastructure.EquipmentRepository;
import com.industrialhub.backend.maintenance.infrastructure.WorkOrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

@Service
public class CreateWorkOrderUseCase {

    private final EquipmentRepository equipmentRepository;
    private final WorkOrderRepository workOrderRepository;
    private final AuditService auditService;

    public CreateWorkOrderUseCase(EquipmentRepository equipmentRepository,
                                   WorkOrderRepository workOrderRepository,
                                   AuditService auditService) {
        this.equipmentRepository = equipmentRepository;
        this.workOrderRepository = workOrderRepository;
        this.auditService = auditService;
    }

    @Transactional
    public WorkOrderResponse execute(CreateWorkOrderRequest request, String username) {
        Equipment equipment = equipmentRepository.findByIdAndActiveTrue(request.equipmentId())
                .orElseThrow(() -> new EquipmentNotFoundException(request.equipmentId()));

        WorkOrder workOrder = WorkOrder.builder()
                .equipment(equipment)
                .type(request.type())
                .title(request.title())
                .description(request.description())
                .priority(request.priority())
                .status(WorkOrderStatus.OPEN)
                .assignedTo(request.assignedTo())
                .openedBy(username)
                .openedAt(LocalDateTime.now())
                .build();

        if (request.type() == WorkOrderType.CORRECTIVE) {
            equipment.setStatus(EquipmentStatus.UNDER_MAINTENANCE);
            equipmentRepository.save(equipment);
        }

        WorkOrder saved = workOrderRepository.save(workOrder);

        auditService.log(username, AuditAction.WORK_ORDER_CREATED, "WorkOrder", saved.getId(),
                Map.of("type", saved.getType().name(), "equipmentId", request.equipmentId().toString()));

        return WorkOrderResponse.from(saved);
    }
}
