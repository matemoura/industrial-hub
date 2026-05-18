package com.industrialhub.backend.qms.application.usecase;

import com.industrialhub.backend.common.application.AuditService;
import com.industrialhub.backend.common.domain.AuditAction;
import com.industrialhub.backend.qms.application.dto.CreateSupplierRequest;
import com.industrialhub.backend.qms.application.dto.SupplierResponse;
import com.industrialhub.backend.qms.domain.Supplier;
import com.industrialhub.backend.qms.domain.SupplierDuplicateCodeException;
import com.industrialhub.backend.qms.infrastructure.SupplierRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
public class CreateSupplierUseCase {

    private final SupplierRepository repository;
    private final AuditService auditService;

    public CreateSupplierUseCase(SupplierRepository repository, AuditService auditService) {
        this.repository = repository;
        this.auditService = auditService;
    }

    @Transactional
    public SupplierResponse execute(CreateSupplierRequest request, String createdBy) {
        if (repository.existsByCode(request.code())) {
            throw new SupplierDuplicateCodeException(request.code());
        }

        Supplier supplier = Supplier.builder()
                .code(request.code())
                .name(request.name())
                .contactEmail(request.contactEmail())
                .contactPhone(request.contactPhone())
                .address(request.address())
                .onboardedAt(request.onboardedAt())
                .build();

        Supplier saved = repository.save(supplier);

        auditService.log(createdBy, AuditAction.SUPPLIER_CREATED, "Supplier", saved.getId(),
                Map.of("code", saved.getCode(), "name", saved.getName()));

        return SupplierResponse.from(saved);
    }
}
