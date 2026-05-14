package com.industrialhub.backend.maintenance.application.usecase;

import com.industrialhub.backend.maintenance.application.dto.EquipmentResponse;
import com.industrialhub.backend.maintenance.domain.EquipmentNotFoundException;
import com.industrialhub.backend.maintenance.infrastructure.EquipmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class GetEquipmentDetailUseCase {

    private final EquipmentRepository repository;

    public GetEquipmentDetailUseCase(EquipmentRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public EquipmentResponse execute(UUID id) {
        return repository.findByIdAndActiveTrue(id)
                .map(EquipmentResponse::from)
                .orElseThrow(() -> new EquipmentNotFoundException(id));
    }
}
