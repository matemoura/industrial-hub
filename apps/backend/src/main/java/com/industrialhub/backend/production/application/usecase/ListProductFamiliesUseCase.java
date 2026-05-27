package com.industrialhub.backend.production.application.usecase;

import com.industrialhub.backend.production.application.dto.ProductFamilyResponse;
import com.industrialhub.backend.production.infrastructure.ProductFamilyRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ListProductFamiliesUseCase {

    private final ProductFamilyRepository repository;

    public ListProductFamiliesUseCase(ProductFamilyRepository repository) {
        this.repository = repository;
    }

    public List<ProductFamilyResponse> execute() {
        return repository.findByActiveTrueOrderByNameAsc()
                .stream()
                .map(ProductFamilyResponse::from)
                .toList();
    }
}
