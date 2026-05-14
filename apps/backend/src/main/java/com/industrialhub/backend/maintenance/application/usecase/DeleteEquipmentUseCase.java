package com.industrialhub.backend.maintenance.application.usecase;

import com.industrialhub.backend.maintenance.domain.Equipment;
import com.industrialhub.backend.maintenance.domain.EquipmentHasOpenOrdersException;
import com.industrialhub.backend.maintenance.domain.EquipmentNotFoundException;
import com.industrialhub.backend.maintenance.domain.WorkOrderStatus;
import com.industrialhub.backend.maintenance.infrastructure.EquipmentRepository;
import com.industrialhub.backend.maintenance.infrastructure.WorkOrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class DeleteEquipmentUseCase {

    private final EquipmentRepository equipmentRepository;
    private final WorkOrderRepository workOrderRepository;

    public DeleteEquipmentUseCase(EquipmentRepository equipmentRepository,
                                   WorkOrderRepository workOrderRepository) {
        this.equipmentRepository = equipmentRepository;
        this.workOrderRepository = workOrderRepository;
    }

    @Transactional
    public void execute(UUID id) {
        Equipment equipment = equipmentRepository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new EquipmentNotFoundException(id));

        if (workOrderRepository.existsByEquipmentIdAndStatusIn(
                id, List.of(WorkOrderStatus.OPEN, WorkOrderStatus.IN_PROGRESS))) {
            throw new EquipmentHasOpenOrdersException(equipment.getCode());
        }

        equipment.setActive(false);
        equipmentRepository.save(equipment);
    }
}
