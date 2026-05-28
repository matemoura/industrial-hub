package com.industrialhub.backend.production.application.usecase;

import com.industrialhub.backend.common.application.AuditService;
import com.industrialhub.backend.common.domain.AuditAction;
import com.industrialhub.backend.maintenance.domain.Equipment;
import com.industrialhub.backend.maintenance.infrastructure.EquipmentRepository;
import com.industrialhub.backend.production.application.dto.CreateSterilizationLoadRequest;
import com.industrialhub.backend.production.application.dto.SterilizationLoadResponse;
import com.industrialhub.backend.production.domain.LoadStatus;
import com.industrialhub.backend.production.domain.SterilizationLoad;
import com.industrialhub.backend.production.infrastructure.SterilizationLoadRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

@Service
public class CreateSterilizationLoadUseCase {

    private final SterilizationLoadRepository loadRepository;
    private final EquipmentRepository equipmentRepository;
    private final AuditService auditService;

    public CreateSterilizationLoadUseCase(SterilizationLoadRepository loadRepository,
                                          EquipmentRepository equipmentRepository,
                                          AuditService auditService) {
        this.loadRepository = loadRepository;
        this.equipmentRepository = equipmentRepository;
        this.auditService = auditService;
    }

    @Transactional
    public SterilizationLoadResponse execute(CreateSterilizationLoadRequest request, String username) {
        int year = java.time.LocalDate.now().getYear();
        int seq  = loadRepository.nextSequenceForYear(year);
        String loadNumber = "CARGA-%d-%03d".formatted(year, seq);

        Equipment sterilizer = null;
        if (request.sterilizerId() != null) {
            sterilizer = equipmentRepository.findByIdAndActiveTrue(request.sterilizerId())
                    .orElseThrow(() -> new com.industrialhub.backend.maintenance.domain.EquipmentNotFoundException(request.sterilizerId()));
        }

        SterilizationLoad load = SterilizationLoad.builder()
                .loadNumber(loadNumber)
                .status(LoadStatus.OPEN)
                .sterilizer(sterilizer)
                .method(request.method())
                .sterilizationDate(request.sterilizationDate())
                .batchCode(request.batchCode())
                .notes(request.notes())
                .createdBy(username)
                .createdAt(LocalDateTime.now())
                .build();

        SterilizationLoad saved = loadRepository.save(load);

        auditService.log(username, AuditAction.STERILIZATION_LOAD_CREATED,
                "SterilizationLoad", saved.getId().toString(),
                Map.of("loadNumber", loadNumber));

        return SterilizationLoadResponse.from(saved);
    }
}
