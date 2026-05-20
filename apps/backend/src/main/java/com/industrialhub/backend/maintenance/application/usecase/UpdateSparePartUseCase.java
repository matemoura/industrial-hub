package com.industrialhub.backend.maintenance.application.usecase;

import com.industrialhub.backend.maintenance.application.dto.SparePartResponse;
import com.industrialhub.backend.maintenance.application.dto.UpdateSparePartRequest;
import com.industrialhub.backend.maintenance.domain.InactiveSparePartException;
import com.industrialhub.backend.maintenance.domain.SparePart;
import com.industrialhub.backend.maintenance.domain.SparePartNotFoundException;
import com.industrialhub.backend.maintenance.infrastructure.SparePartRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class UpdateSparePartUseCase {

    private final SparePartRepository sparePartRepository;

    public UpdateSparePartUseCase(SparePartRepository sparePartRepository) {
        this.sparePartRepository = sparePartRepository;
    }

    @Transactional
    public SparePartResponse execute(UUID id, UpdateSparePartRequest request) {
        SparePart part = sparePartRepository.findById(id)
                .orElseThrow(() -> new SparePartNotFoundException(id));
        if (!part.isActive()) {
            throw new InactiveSparePartException();
        }
        part.setName(request.name());
        part.setCategory(request.category());
        part.setUnit(request.unit());
        if (request.minStockQty() != null) {
            part.setMinStockQty(request.minStockQty());
        }
        return SparePartResponse.from(sparePartRepository.save(part));
    }
}
