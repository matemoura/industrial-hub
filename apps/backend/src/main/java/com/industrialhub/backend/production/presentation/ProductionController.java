package com.industrialhub.backend.production.presentation;

import com.industrialhub.backend.production.application.dto.*;
import com.industrialhub.backend.production.application.usecase.*;
import com.industrialhub.backend.production.domain.ProductionImportType;
import com.industrialhub.backend.production.domain.ProductionOrderStatus;
import com.industrialhub.backend.production.domain.ProductType;
import com.industrialhub.backend.production.domain.ProductionOrderDisplayStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/production")
@Validated
public class ProductionController {

    private final ImportProductCatalogUseCase importProductCatalog;
    private final ImportStockSnapshotUseCase importStockSnapshot;
    private final ImportProductionOrdersUseCase importProductionOrders;
    private final ImportCycleTimesUseCase importCycleTimes;
    private final ImportLeadTimesUseCase importLeadTimes;
    private final ListProductFamiliesUseCase listFamilies;
    private final ListProductsUseCase listProducts;
    private final ListStockSnapshotsUseCase listStock;
    private final ListProductionOrdersUseCase listOrders;
    private final ListCycleTimesUseCase listCycleTimes;
    private final GetImportHistoryUseCase importHistory;
    private final GetProductionTrackingUseCase getTracking;
    private final GetProductionSummaryUseCase getSummary;
    private final ListProductionOrdersForTrackingUseCase listTrackingOrders;

    public ProductionController(
            ImportProductCatalogUseCase importProductCatalog,
            ImportStockSnapshotUseCase importStockSnapshot,
            ImportProductionOrdersUseCase importProductionOrders,
            ImportCycleTimesUseCase importCycleTimes,
            ImportLeadTimesUseCase importLeadTimes,
            ListProductFamiliesUseCase listFamilies,
            ListProductsUseCase listProducts,
            ListStockSnapshotsUseCase listStock,
            ListProductionOrdersUseCase listOrders,
            ListCycleTimesUseCase listCycleTimes,
            GetImportHistoryUseCase importHistory,
            GetProductionTrackingUseCase getTracking,
            GetProductionSummaryUseCase getSummary,
            ListProductionOrdersForTrackingUseCase listTrackingOrders) {
        this.importProductCatalog = importProductCatalog;
        this.importStockSnapshot = importStockSnapshot;
        this.importProductionOrders = importProductionOrders;
        this.importCycleTimes = importCycleTimes;
        this.importLeadTimes = importLeadTimes;
        this.listFamilies = listFamilies;
        this.listProducts = listProducts;
        this.listStock = listStock;
        this.listOrders = listOrders;
        this.listCycleTimes = listCycleTimes;
        this.importHistory = importHistory;
        this.getTracking = getTracking;
        this.getSummary = getSummary;
        this.listTrackingOrders = listTrackingOrders;
    }

    // ===== Import endpoints =====

    @PostMapping("/import/products")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public ImportProductionBatchResponse importProducts(
            @RequestParam("file") MultipartFile file,
            Authentication authentication) {
        return importProductCatalog.execute(file, authentication.getName());
    }

    @PostMapping("/import/stock")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('SUPERVISOR','ADMIN')")
    public ImportProductionBatchResponse importStock(
            @RequestParam("file") MultipartFile file,
            Authentication authentication) {
        return importStockSnapshot.execute(file, authentication.getName());
    }

    @PostMapping("/import/production-orders")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('SUPERVISOR','ADMIN')")
    public ImportProductionBatchResponse importProductionOrders(
            @RequestParam("file") MultipartFile file,
            Authentication authentication) {
        return importProductionOrders.execute(file, authentication.getName());
    }

    @PostMapping("/import/cycle-times")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public ImportProductionBatchResponse importCycleTimes(
            @RequestParam("file") MultipartFile file,
            Authentication authentication) {
        return importCycleTimes.execute(file, authentication.getName());
    }

    @PostMapping("/import/lead-times")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public ImportProductionBatchResponse importLeadTimes(
            @RequestParam("file") MultipartFile file,
            Authentication authentication) {
        return importLeadTimes.execute(file, authentication.getName());
    }

    @GetMapping("/import/history")
    @PreAuthorize("hasAnyRole('SUPERVISOR','ADMIN')")
    public Page<ImportProductionBatchResponse> importHistory(
            @RequestParam(required = false) ProductionImportType type,
            @PageableDefault(size = 20, sort = "importedAt") Pageable pageable) {
        return importHistory.list(type, pageable);
    }

    @GetMapping("/import/history/{id}")
    @PreAuthorize("hasAnyRole('SUPERVISOR','ADMIN')")
    public ImportProductionBatchResponse importHistoryById(@PathVariable UUID id) {
        return importHistory.getById(id);
    }

    // ===== Product / Family read endpoints =====

    @GetMapping("/families")
    @PreAuthorize("hasAnyRole('OPERATOR','SUPERVISOR','ADMIN')")
    public List<ProductFamilyResponse> listFamilies() {
        return listFamilies.execute();
    }

    @GetMapping("/products")
    @PreAuthorize("hasAnyRole('OPERATOR','SUPERVISOR','ADMIN')")
    public Page<ProductSummaryResponse> listProducts(
            @RequestParam(required = false) String familyCode,
            @RequestParam(required = false) ProductType type,
            @RequestParam(required = false, defaultValue = "true") boolean active,
            @PageableDefault(size = 20) Pageable pageable) {
        return listProducts.list(familyCode, type, active, pageable);
    }

    @GetMapping("/products/{id}")
    @PreAuthorize("hasAnyRole('OPERATOR','SUPERVISOR','ADMIN')")
    public ProductDetailResponse getProduct(@PathVariable UUID id) {
        return listProducts.getDetail(id);
    }

    @GetMapping("/products/{id}/cycle-times")
    @PreAuthorize("hasAnyRole('OPERATOR','SUPERVISOR','ADMIN')")
    public List<CycleTimeResponse> getCycleTimesForProduct(@PathVariable UUID id) {
        return listCycleTimes.execute(id);
    }

    // ===== Stock endpoints =====

    @GetMapping("/stock")
    @PreAuthorize("hasAnyRole('SUPERVISOR','ADMIN')")
    public List<StockPositionResponse> listStock(
            @RequestParam(required = false) Boolean belowMin) {
        return listStock.listLatestPositions(belowMin);
    }

    // ===== Production Orders endpoints =====

    @GetMapping("/production-orders")
    @PreAuthorize("hasAnyRole('OPERATOR','SUPERVISOR','ADMIN')")
    public Page<ProductionOrderSummaryResponse> listOrders(
            @RequestParam(required = false) String familyCode,
            @RequestParam(required = false) ProductionOrderStatus status,
            @RequestParam(required = false) ProductType productType,
            @RequestParam(required = false, defaultValue = "false") boolean overdueOnly,
            @PageableDefault(size = 20, sort = "dueDate") Pageable pageable) {
        return listOrders.execute(familyCode, status, productType, overdueOnly, pageable);
    }

    // ===== Cycle Times endpoints =====

    @GetMapping("/cycle-times")
    @PreAuthorize("hasAnyRole('OPERATOR','SUPERVISOR','ADMIN')")
    public List<CycleTimeResponse> listCycleTimes(
            @RequestParam(required = false) UUID productId) {
        return listCycleTimes.execute(productId);
    }

    // ===== Tracking endpoints (US-082 / ADR-041) =====

    @GetMapping("/tracking/families")
    @PreAuthorize("isAuthenticated()")
    public ProductionTrackingResponse getTracking(
            @RequestParam(required = false) String familyCode,
            @RequestParam(required = false) Boolean overdue) {
        return getTracking.execute(familyCode, overdue);
    }

    @GetMapping("/tracking/orders")
    @PreAuthorize("isAuthenticated()")
    public ProductionOrderListResponse getTrackingOrders(
            @RequestParam(required = false) String familyCode,
            @RequestParam(required = false) String displayStatus,
            @RequestParam(required = false) Boolean overdue,
            @RequestParam(required = false) String productType,
            @PageableDefault(size = 50) Pageable pageable) {
        return listTrackingOrders.execute(familyCode, displayStatus, overdue, productType, pageable);
    }

    @GetMapping("/tracking/summary")
    @PreAuthorize("isAuthenticated()")
    public ProductionSummaryResponse getSummary() {
        return getSummary.execute();
    }
}
