package com.industrialhub.backend.maintenance.application.usecase;

import com.industrialhub.backend.common.application.PlantContext;
import com.industrialhub.backend.maintenance.application.dto.EquipmentResponse;
import com.industrialhub.backend.maintenance.domain.Equipment;
import com.industrialhub.backend.maintenance.domain.EquipmentStatus;
import com.industrialhub.backend.maintenance.domain.EquipmentType;
import com.industrialhub.backend.maintenance.infrastructure.EquipmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

@Service
public class GetEquipmentListUseCase {

    private final EquipmentRepository repository;

    public GetEquipmentListUseCase(EquipmentRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<EquipmentResponse> execute(EquipmentType type, EquipmentStatus status) {
        boolean isAdmin = PlantContext.isAdminContext();
        List<UUID> plantIds = PlantContext.current();

        // If not admin and no plant association, return empty
        if (!isAdmin && plantIds.isEmpty()) {
            return List.of();
        }

        List<Equipment> equipment;
        if (isAdmin) {
            // ADMIN: use existing queries without plant filter
            if (type == null && status == null) {
                equipment = repository.findByActiveTrueOrderByNameAsc();
            } else if (type != null && status == null) {
                equipment = repository.findByActiveTrueAndTypeOrderByNameAsc(type);
            } else if (type == null) {
                equipment = repository.findByActiveTrueAndStatusOrderByNameAsc(status);
            } else {
                equipment = repository.findByActiveTrueAndTypeAndStatusOrderByNameAsc(type, status);
            }
        } else {
            // Non-ADMIN: filter by plant, then apply type/status in Java
            equipment = repository.findActiveByPlantIds(plantIds);
            Stream<Equipment> stream = equipment.stream();
            if (type != null) stream = stream.filter(e -> e.getType() == type);
            if (status != null) stream = stream.filter(e -> e.getStatus() == status);
            equipment = stream.toList();
        }

        return equipment.stream().map(EquipmentResponse::from).toList();
    }
}
