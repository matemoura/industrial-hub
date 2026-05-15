package com.industrialhub.backend.maintenance.application.usecase;

import com.industrialhub.backend.common.application.AuditService;
import com.industrialhub.backend.common.domain.AuditAction;
import com.industrialhub.backend.maintenance.domain.Equipment;
import com.industrialhub.backend.maintenance.domain.EquipmentHasOpenOrdersException;
import com.industrialhub.backend.maintenance.domain.EquipmentNotFoundException;
import com.industrialhub.backend.maintenance.domain.WorkOrderStatus;
import com.industrialhub.backend.maintenance.infrastructure.EquipmentRepository;
import com.industrialhub.backend.maintenance.infrastructure.WorkOrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class DeleteEquipmentUseCase {

    private final EquipmentRepository equipmentRepository;
    private final WorkOrderRepository workOrderRepository;
    private final AuditService auditService;

    public DeleteEquipmentUseCase(EquipmentRepository equipmentRepository,
                                   WorkOrderRepository workOrderRepository,
                                   AuditService auditService) {
        this.equipmentRepository = equipmentRepository;
        this.workOrderRepository = workOrderRepository;
        this.auditService = auditService;
    }

    @Transactional
    public void execute(UUID id, String username) {
        Equipment equipment = equipmentRepository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new EquipmentNotFoundException(id));

        if (workOrderRepository.existsByEquipmentIdAndStatusIn(
                id, List.of(WorkOrderStatus.OPEN, WorkOrderStatus.IN_PROGRESS))) {
            throw new EquipmentHasOpenOrdersException(equipment.getCode());
        }

        equipment.setActive(false);
        equipmentRepository.save(equipment);

        auditService.log(username, AuditAction.EQUIPMENT_DELETED, "Equipment", id,
                Map.of("code", equipment.getCode()));
    }
}
