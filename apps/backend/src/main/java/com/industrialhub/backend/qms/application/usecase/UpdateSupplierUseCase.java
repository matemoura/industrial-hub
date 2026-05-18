package com.industrialhub.backend.qms.application.usecase;

import com.industrialhub.backend.common.application.AuditService;
import com.industrialhub.backend.common.domain.AuditAction;
import com.industrialhub.backend.qms.application.dto.CreateSupplierRequest;
import com.industrialhub.backend.qms.application.dto.SupplierResponse;
import com.industrialhub.backend.qms.domain.SupplierNotFoundException;
import com.industrialhub.backend.qms.infrastructure.SupplierRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
public class UpdateSupplierUseCase {

    private final SupplierRepository repository;
    private final AuditService auditService;

    public UpdateSupplierUseCase(SupplierRepository repository, AuditService auditService) {
        this.repository = repository;
        this.auditService = auditService;
    }

    @Transactional
    public SupplierResponse execute(UUID id, CreateSupplierRequest request, String username) {
        var supplier = repository.findById(id)
                .orElseThrow(() -> new SupplierNotFoundException(id));

        supplier.setName(request.name());
        supplier.setContactEmail(request.contactEmail());
        supplier.setContactPhone(request.contactPhone());
        supplier.setAddress(request.address());

        var saved = repository.save(supplier);

        auditService.log(username, AuditAction.SUPPLIER_UPDATED, "Supplier", id,
                Map.of("name", saved.getName(), "contactEmail",
                        saved.getContactEmail() != null ? saved.getContactEmail() : ""));

        return SupplierResponse.from(saved);
    }
}
