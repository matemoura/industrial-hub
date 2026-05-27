package com.industrialhub.backend.production.application.usecase;

import com.industrialhub.backend.production.application.dto.StockPositionResponse;
import com.industrialhub.backend.production.infrastructure.StockSnapshotRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class ListStockSnapshotsUseCase {

    private final StockSnapshotRepository stockSnapshotRepository;

    public ListStockSnapshotsUseCase(StockSnapshotRepository stockSnapshotRepository) {
        this.stockSnapshotRepository = stockSnapshotRepository;
    }

    public List<StockPositionResponse> listLatestPositions(Boolean belowMin) {
        return stockSnapshotRepository.findLatestPerProduct()
                .stream()
                .map(StockPositionResponse::from)
                .filter(r -> belowMin == null || !belowMin || r.belowMin())
                .toList();
    }

    public List<StockPositionResponse> listFiltered(UUID productId, LocalDate from, LocalDate to) {
        return stockSnapshotRepository.findFiltered(productId, from, to)
                .stream()
                .map(StockPositionResponse::from)
                .toList();
    }
}
