package com.industrialhub.backend.qms.application.usecase;

import com.industrialhub.backend.qms.application.dto.SupplierResponse;
import com.industrialhub.backend.qms.infrastructure.SupplierRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class GetSupplierListUseCase {

    private final SupplierRepository repository;

    public GetSupplierListUseCase(SupplierRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<SupplierResponse> execute() {
        return repository.findAllByActiveTrue().stream()
                .map(SupplierResponse::from)
                .toList();
    }
}
