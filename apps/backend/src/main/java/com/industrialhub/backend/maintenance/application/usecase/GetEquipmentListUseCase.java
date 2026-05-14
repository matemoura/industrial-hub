package com.industrialhub.backend.maintenance.application.usecase;

import com.industrialhub.backend.maintenance.application.dto.EquipmentResponse;
import com.industrialhub.backend.maintenance.domain.EquipmentStatus;
import com.industrialhub.backend.maintenance.domain.EquipmentType;
import com.industrialhub.backend.maintenance.infrastructure.EquipmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class GetEquipmentListUseCase {

    private final EquipmentRepository repository;

    public GetEquipmentListUseCase(EquipmentRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<EquipmentResponse> execute(EquipmentType type, EquipmentStatus status) {
        if (type == null && status == null) {
            return repository.findByActiveTrueOrderByNameAsc()
                    .stream().map(EquipmentResponse::from).toList();
        }
        if (type != null && status == null) {
            return repository.findByActiveTrueAndTypeOrderByNameAsc(type)
                    .stream().map(EquipmentResponse::from).toList();
        }
        if (type == null) {
            return repository.findByActiveTrueAndStatusOrderByNameAsc(status)
                    .stream().map(EquipmentResponse::from).toList();
        }
        return repository.findByActiveTrueAndTypeAndStatusOrderByNameAsc(type, status)
                .stream().map(EquipmentResponse::from).toList();
    }
}
