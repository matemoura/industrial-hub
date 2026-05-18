package com.industrialhub.backend.qms.application.usecase;

import com.industrialhub.backend.common.application.AuditService;
import com.industrialhub.backend.common.domain.AuditAction;
import com.industrialhub.backend.qms.domain.SupplierNotFoundException;
import com.industrialhub.backend.qms.infrastructure.SupplierRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
public class DeactivateSupplierUseCase {

    private final SupplierRepository repository;
    private final AuditService auditService;

    public DeactivateSupplierUseCase(SupplierRepository repository, AuditService auditService) {
        this.repository = repository;
        this.auditService = auditService;
    }

    @Transactional
    public void execute(UUID id, String deactivatedBy) {
        var supplier = repository.findById(id)
                .orElseThrow(() -> new SupplierNotFoundException(id));

        supplier.setActive(false);
        repository.save(supplier);

        auditService.log(deactivatedBy, AuditAction.SUPPLIER_DEACTIVATED, "Supplier", id,
                Map.of("code", supplier.getCode()));
    }
}
