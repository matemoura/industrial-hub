package com.industrialhub.backend.maintenance.application.usecase;

import com.industrialhub.backend.common.application.AuditService;
import com.industrialhub.backend.common.domain.AuditAction;
import com.industrialhub.backend.maintenance.application.dto.CreateSparePartRequest;
import com.industrialhub.backend.maintenance.application.dto.SparePartResponse;
import com.industrialhub.backend.maintenance.domain.SparePart;
import com.industrialhub.backend.maintenance.domain.SparePartDuplicateCodeException;
import com.industrialhub.backend.maintenance.infrastructure.SparePartRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
public class CreateSparePartUseCase {

    private final SparePartRepository sparePartRepository;
    private final AuditService auditService;

    public CreateSparePartUseCase(SparePartRepository sparePartRepository,
                                   AuditService auditService) {
        this.sparePartRepository = sparePartRepository;
        this.auditService = auditService;
    }

    @Transactional
    public SparePartResponse execute(CreateSparePartRequest request, String username) {
        SparePart part = SparePart.builder()
                .code(request.code().trim())
                .name(request.name())
                .category(request.category())
                .unit(request.unit())
                .stockQty(request.stockQty())
                .minStockQty(request.minStockQty())
                .build();
        SparePart saved;
        try {
            saved = sparePartRepository.save(part);
            sparePartRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw new SparePartDuplicateCodeException(request.code());
        }
        auditService.log(username, AuditAction.PART_CREATED, "SparePart",
                saved.getId().toString(), Map.of("code", saved.getCode()));
        return SparePartResponse.from(saved);
    }
}
