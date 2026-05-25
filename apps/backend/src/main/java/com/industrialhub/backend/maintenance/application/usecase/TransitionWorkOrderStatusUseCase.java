package com.industrialhub.backend.maintenance.application.usecase;

import com.industrialhub.backend.common.application.AuditService;
import com.industrialhub.backend.common.application.dto.webhook.WorkOrderStatusChangedWebhookPayload;
import com.industrialhub.backend.common.domain.AuditAction;
import com.industrialhub.backend.common.webhook.domain.WebhookEvent;
import com.industrialhub.backend.common.webhook.service.WebhookDispatchService;
import com.industrialhub.backend.maintenance.application.dto.WorkOrderResponse;
import com.industrialhub.backend.maintenance.domain.Equipment;
import com.industrialhub.backend.maintenance.domain.EquipmentStatus;
import com.industrialhub.backend.maintenance.domain.InvalidWorkOrderTransitionException;
import com.industrialhub.backend.maintenance.domain.WorkOrder;
import com.industrialhub.backend.maintenance.domain.WorkOrderNotFoundException;
import com.industrialhub.backend.maintenance.domain.WorkOrderStatus;
import com.industrialhub.backend.maintenance.domain.WorkOrderType;
import com.industrialhub.backend.maintenance.infrastructure.EquipmentRepository;
import com.industrialhub.backend.maintenance.infrastructure.WorkOrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class TransitionWorkOrderStatusUseCase {

    private static final Map<WorkOrderStatus, List<WorkOrderStatus>> ALLOWED = Map.of(
        WorkOrderStatus.OPEN,        List.of(WorkOrderStatus.IN_PROGRESS, WorkOrderStatus.CANCELLED),
        WorkOrderStatus.IN_PROGRESS, List.of(WorkOrderStatus.DONE, WorkOrderStatus.CANCELLED),
        WorkOrderStatus.DONE,        List.of(),
        WorkOrderStatus.CANCELLED,   List.of()
    );

    private final WorkOrderRepository workOrderRepository;
    private final EquipmentRepository equipmentRepository;
    private final AuditService auditService;
    private final WebhookDispatchService webhookDispatchService;

    public TransitionWorkOrderStatusUseCase(WorkOrderRepository workOrderRepository,
                                             EquipmentRepository equipmentRepository,
                                             AuditService auditService,
                                             WebhookDispatchService webhookDispatchService) {
        this.workOrderRepository = workOrderRepository;
        this.equipmentRepository = equipmentRepository;
        this.auditService = auditService;
        this.webhookDispatchService = webhookDispatchService;
    }

    @Transactional
    public WorkOrderResponse execute(UUID id, WorkOrderStatus targetStatus, String username) {
        WorkOrder workOrder = workOrderRepository.findById(id)
                .orElseThrow(() -> new WorkOrderNotFoundException(id));

        List<WorkOrderStatus> allowed = ALLOWED.get(workOrder.getStatus());
        if (!allowed.contains(targetStatus)) {
            throw new InvalidWorkOrderTransitionException(workOrder.getStatus(), targetStatus, allowed);
        }

        WorkOrderStatus previousStatus = workOrder.getStatus();
        workOrder.setStatus(targetStatus);

        switch (targetStatus) {
            case IN_PROGRESS -> workOrder.setStartedAt(LocalDateTime.now());
            case DONE, CANCELLED -> workOrder.setClosedAt(LocalDateTime.now());
            default -> { /* no timestamp change needed */ }
        }

        if (targetStatus == WorkOrderStatus.DONE && workOrder.getType() == WorkOrderType.CORRECTIVE) {
            Equipment equipment = workOrder.getEquipment();
            List<WorkOrder> openCorrective = workOrderRepository.findOpenCorrectiveByEquipmentId(equipment.getId());
            if (openCorrective.isEmpty()) {
                equipment.setStatus(EquipmentStatus.OPERATIONAL);
                equipmentRepository.save(equipment);
            }
        }

        WorkOrder saved = workOrderRepository.save(workOrder);

        auditService.log(username, AuditAction.WORK_ORDER_STATUS_CHANGED, "WorkOrder", id,
                Map.of("from", previousStatus.name(), "to", targetStatus.name()));

        webhookDispatchService.dispatch(WebhookEvent.WORK_ORDER_STATUS_CHANGED,
                new WorkOrderStatusChangedWebhookPayload(id, saved.getTitle(), previousStatus, targetStatus, username));

        return WorkOrderResponse.from(saved);
    }
}
