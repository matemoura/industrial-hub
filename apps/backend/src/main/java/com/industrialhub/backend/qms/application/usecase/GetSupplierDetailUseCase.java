package com.industrialhub.backend.qms.application.usecase;

import com.industrialhub.backend.qms.application.dto.SupplierResponse;
import com.industrialhub.backend.qms.domain.SupplierNotFoundException;
import com.industrialhub.backend.qms.infrastructure.SupplierRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class GetSupplierDetailUseCase {

    private final SupplierRepository repository;

    public GetSupplierDetailUseCase(SupplierRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public SupplierResponse execute(UUID id) {
        return repository.findById(id)
                .map(SupplierResponse::from)
                .orElseThrow(() -> new SupplierNotFoundException(id));
    }
}
