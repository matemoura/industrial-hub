package com.industrialhub.backend.maintenance.application.usecase;

import com.industrialhub.backend.maintenance.application.dto.EquipmentResponse;
import com.industrialhub.backend.maintenance.application.dto.UpdateEquipmentRequest;
import com.industrialhub.backend.maintenance.domain.Equipment;
import com.industrialhub.backend.maintenance.domain.EquipmentNotFoundException;
import com.industrialhub.backend.maintenance.infrastructure.EquipmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class UpdateEquipmentUseCase {

    private final EquipmentRepository repository;

    public UpdateEquipmentUseCase(EquipmentRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public EquipmentResponse execute(UUID id, UpdateEquipmentRequest request) {
        Equipment equipment = repository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new EquipmentNotFoundException(id));

        equipment.setName(request.name());
        equipment.setLocation(request.location());
        equipment.setType(request.type());
        equipment.setAcquiredAt(request.acquiredAt());

        Equipment saved = repository.save(equipment);
        return EquipmentResponse.from(saved);
    }
}
