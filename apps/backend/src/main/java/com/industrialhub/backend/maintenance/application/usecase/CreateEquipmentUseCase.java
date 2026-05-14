package com.industrialhub.backend.maintenance.application.usecase;

import com.industrialhub.backend.maintenance.application.dto.CreateEquipmentRequest;
import com.industrialhub.backend.maintenance.application.dto.EquipmentResponse;
import com.industrialhub.backend.maintenance.domain.Equipment;
import com.industrialhub.backend.maintenance.domain.EquipmentDuplicateCodeException;
import com.industrialhub.backend.maintenance.domain.EquipmentStatus;
import com.industrialhub.backend.maintenance.infrastructure.EquipmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreateEquipmentUseCase {

    private final EquipmentRepository repository;

    public CreateEquipmentUseCase(EquipmentRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public EquipmentResponse execute(CreateEquipmentRequest request) {
        if (repository.existsByCode(request.code())) {
            throw new EquipmentDuplicateCodeException(request.code());
        }

        Equipment equipment = Equipment.builder()
                .code(request.code())
                .name(request.name())
                .location(request.location())
                .type(request.type())
                .status(EquipmentStatus.OPERATIONAL)
                .acquiredAt(request.acquiredAt())
                .active(true)
                .build();

        Equipment saved = repository.save(equipment);
        return EquipmentResponse.from(saved);
    }
}
