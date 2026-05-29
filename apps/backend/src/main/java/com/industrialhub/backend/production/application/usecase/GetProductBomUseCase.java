package com.industrialhub.backend.production.application.usecase;

import com.industrialhub.backend.production.application.dto.BomComponentRow;
import com.industrialhub.backend.production.infrastructure.ProductComponentRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * ADR-044 — retorna a estrutura BOM de um produto por dynamicsCode.
 * Lista vazia se o produto não tem BOM cadastrado.
 */
@Service
public class GetProductBomUseCase {

    private final ProductComponentRepository componentRepository;

    public GetProductBomUseCase(ProductComponentRepository componentRepository) {
        this.componentRepository = componentRepository;
    }

    public List<BomComponentRow> execute(String productCode) {
        return componentRepository.findByParentProductCode(productCode)
                .stream()
                .map(BomComponentRow::from)
                .toList();
    }
}
