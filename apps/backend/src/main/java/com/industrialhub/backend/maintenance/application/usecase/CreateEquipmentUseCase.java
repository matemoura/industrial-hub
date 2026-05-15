package com.industrialhub.backend.maintenance.application.usecase;

import com.industrialhub.backend.common.application.AuditService;
import com.industrialhub.backend.common.domain.AuditAction;
import com.industrialhub.backend.maintenance.application.dto.CreateEquipmentRequest;
import com.industrialhub.backend.maintenance.application.dto.EquipmentResponse;
import com.industrialhub.backend.maintenance.domain.Equipment;
import com.industrialhub.backend.maintenance.domain.EquipmentDuplicateCodeException;
import com.industrialhub.backend.maintenance.domain.EquipmentStatus;
import com.industrialhub.backend.maintenance.infrastructure.EquipmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
public class CreateEquipmentUseCase {

    private final EquipmentRepository repository;
    private final AuditService auditService;

    public CreateEquipmentUseCase(EquipmentRepository repository, AuditService auditService) {
        this.repository = repository;
        this.auditService = auditService;
    }

    @Transactional
    public EquipmentResponse execute(CreateEquipmentRequest request, String username) {
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

        auditService.log(username, AuditAction.EQUIPMENT_CREATED, "Equipment", saved.getId(),
                Map.of("code", saved.getCode(), "name", saved.getName()));

        return EquipmentResponse.from(saved);
    }
}
