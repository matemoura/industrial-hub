package com.industrialhub.backend.qms.application.usecase;

import com.industrialhub.backend.qms.application.dto.CreateSupplierRequest;
import com.industrialhub.backend.qms.application.dto.SupplierResponse;
import com.industrialhub.backend.qms.domain.SupplierNotFoundException;
import com.industrialhub.backend.qms.infrastructure.SupplierRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class UpdateSupplierUseCase {

    private final SupplierRepository repository;

    public UpdateSupplierUseCase(SupplierRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public SupplierResponse execute(UUID id, CreateSupplierRequest request) {
        var supplier = repository.findById(id)
                .orElseThrow(() -> new SupplierNotFoundException(id));

        supplier.setName(request.name());
        supplier.setContactEmail(request.contactEmail());
        supplier.setContactPhone(request.contactPhone());
        supplier.setAddress(request.address());
        supplier.setOnboardedAt(request.onboardedAt());

        return SupplierResponse.from(repository.save(supplier));
    }
}
