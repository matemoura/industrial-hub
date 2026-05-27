package com.industrialhub.backend.production.application.usecase;

import com.industrialhub.backend.production.application.dto.ProductDetailResponse;
import com.industrialhub.backend.production.application.dto.ProductSummaryResponse;
import com.industrialhub.backend.production.domain.Product;
import com.industrialhub.backend.production.domain.ProductNotFoundException;
import com.industrialhub.backend.production.domain.ProductType;
import com.industrialhub.backend.production.infrastructure.CycleTimeRepository;
import com.industrialhub.backend.production.infrastructure.ProductRepository;
import com.industrialhub.backend.production.infrastructure.StockSnapshotRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class ListProductsUseCase {

    private final ProductRepository productRepository;
    private final StockSnapshotRepository stockSnapshotRepository;
    private final CycleTimeRepository cycleTimeRepository;

    public ListProductsUseCase(ProductRepository productRepository,
                                StockSnapshotRepository stockSnapshotRepository,
                                CycleTimeRepository cycleTimeRepository) {
        this.productRepository = productRepository;
        this.stockSnapshotRepository = stockSnapshotRepository;
        this.cycleTimeRepository = cycleTimeRepository;
    }

    public Page<ProductSummaryResponse> list(String familyCode, ProductType type, boolean active, Pageable pageable) {
        return productRepository.findFiltered(familyCode, type, active, pageable)
                .map(ProductSummaryResponse::from);
    }

    public ProductDetailResponse getDetail(UUID id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));

        Integer currentStockQty = stockSnapshotRepository.findTopByProductIdOrderBySnapshotDateDesc(id)
                .map(s -> s.getQty())
                .orElse(null);

        Double currentCycleTimeSeconds = cycleTimeRepository.findTopByProductIdOrderByEffectiveDateDesc(id)
                .map(c -> c.getSecondsPerUnit())
                .orElse(null);

        return ProductDetailResponse.from(product, currentStockQty, currentCycleTimeSeconds);
    }
}
