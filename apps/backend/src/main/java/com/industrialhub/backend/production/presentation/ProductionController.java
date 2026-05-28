package com.industrialhub.backend.production.presentation;

import com.industrialhub.backend.production.application.dto.*;
import com.industrialhub.backend.production.application.usecase.*;
import com.industrialhub.backend.production.domain.LoadStatus;
import com.industrialhub.backend.production.domain.ProductionImportType;
import com.industrialhub.backend.production.domain.ProductionOrderStatus;
import com.industrialhub.backend.production.domain.ProductType;
import com.industrialhub.backend.production.domain.ProductionOrderDisplayStatus;
import com.industrialhub.backend.production.domain.SterilizationMethod;
import jakarta.validation.Valid;
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
    private final CreateSterilizationLoadUseCase createLoad;
    private final ListSterilizationLoadsUseCase listLoads;
    private final GetSterilizationLoadDetailUseCase getLoadDetail;
    private final GetPendingOrdersForLoadUseCase getPendingOrders;
    private final AddOrderToLoadUseCase addOrderToLoad;
    private final RemoveOrderFromLoadUseCase removeOrderFromLoad;
    private final TransitionLoadStatusUseCase transitionLoadStatus;

    // ===== Sprint 32 — MRP, Staffing, Planning =====
    private final DryRunMrpUseCase dryRunMrp;
    private final RunMrpUseCase runMrp;
    private final GetMrpRunsUseCase getMrpRuns;
    private final GetMrpSuggestionsUseCase getMrpSuggestions;
    private final AcceptMrpSuggestionUseCase acceptMrpSuggestion;
    private final RejectMrpSuggestionUseCase rejectMrpSuggestion;
    private final ConvertMrpSuggestionUseCase convertMrpSuggestion;
    private final GetStaffingConfigUseCase getStaffingConfig;
    private final UpdateStaffingConfigUseCase updateStaffingConfig;
    private final UpdateOrderStaffingUseCase updateOrderStaffing;
    private final ResetOrderStaffingUseCase resetOrderStaffing;
    private final GetPlanningBoardUseCase getPlanningBoard;
    private final GetPlanningTimelineUseCase getPlanningTimeline;
    private final GetPurchaseNeedsUseCase getPurchaseNeeds;

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
            ListProductionOrdersForTrackingUseCase listTrackingOrders,
            CreateSterilizationLoadUseCase createLoad,
            ListSterilizationLoadsUseCase listLoads,
            GetSterilizationLoadDetailUseCase getLoadDetail,
            GetPendingOrdersForLoadUseCase getPendingOrders,
            AddOrderToLoadUseCase addOrderToLoad,
            RemoveOrderFromLoadUseCase removeOrderFromLoad,
            TransitionLoadStatusUseCase transitionLoadStatus,
            DryRunMrpUseCase dryRunMrp,
            RunMrpUseCase runMrp,
            GetMrpRunsUseCase getMrpRuns,
            GetMrpSuggestionsUseCase getMrpSuggestions,
            AcceptMrpSuggestionUseCase acceptMrpSuggestion,
            RejectMrpSuggestionUseCase rejectMrpSuggestion,
            ConvertMrpSuggestionUseCase convertMrpSuggestion,
            GetStaffingConfigUseCase getStaffingConfig,
            UpdateStaffingConfigUseCase updateStaffingConfig,
            UpdateOrderStaffingUseCase updateOrderStaffing,
            ResetOrderStaffingUseCase resetOrderStaffing,
            GetPlanningBoardUseCase getPlanningBoard,
            GetPlanningTimelineUseCase getPlanningTimeline,
            GetPurchaseNeedsUseCase getPurchaseNeeds) {
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
        this.createLoad = createLoad;
        this.listLoads = listLoads;
        this.getLoadDetail = getLoadDetail;
        this.getPendingOrders = getPendingOrders;
        this.addOrderToLoad = addOrderToLoad;
        this.removeOrderFromLoad = removeOrderFromLoad;
        this.transitionLoadStatus = transitionLoadStatus;
        this.dryRunMrp = dryRunMrp;
        this.runMrp = runMrp;
        this.getMrpRuns = getMrpRuns;
        this.getMrpSuggestions = getMrpSuggestions;
        this.acceptMrpSuggestion = acceptMrpSuggestion;
        this.rejectMrpSuggestion = rejectMrpSuggestion;
        this.convertMrpSuggestion = convertMrpSuggestion;
        this.getStaffingConfig = getStaffingConfig;
        this.updateStaffingConfig = updateStaffingConfig;
        this.updateOrderStaffing = updateOrderStaffing;
        this.resetOrderStaffing = resetOrderStaffing;
        this.getPlanningBoard = getPlanningBoard;
        this.getPlanningTimeline = getPlanningTimeline;
        this.getPurchaseNeeds = getPurchaseNeeds;
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
    @PreAuthorize("hasAnyRole('OPERATOR','SUPERVISOR','ADMIN')")
    public ProductionTrackingResponse getTracking(
            @RequestParam(required = false) String familyCode,
            @RequestParam(required = false) Boolean overdue) {
        return getTracking.execute(familyCode, overdue);
    }

    @GetMapping("/tracking/orders")
    @PreAuthorize("hasAnyRole('OPERATOR','SUPERVISOR','ADMIN')")
    public ProductionOrderListResponse getTrackingOrders(
            @RequestParam(required = false) String familyCode,
            @RequestParam(required = false) String displayStatus,
            @RequestParam(required = false) Boolean overdue,
            @RequestParam(required = false) String productType,
            @PageableDefault(size = 50) Pageable pageable) {
        return listTrackingOrders.execute(familyCode, displayStatus, overdue, productType, pageable);
    }

    @GetMapping("/tracking/summary")
    @PreAuthorize("hasAnyRole('OPERATOR','SUPERVISOR','ADMIN')")
    public ProductionSummaryResponse getSummary() {
        return getSummary.execute();
    }

    // ===== Sterilization Loads endpoints (US-084 / ADR-029, ADR-042) =====

    @PostMapping("/sterilization-loads")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('SUPERVISOR','ADMIN')")
    public SterilizationLoadResponse createLoad(
            @Valid @RequestBody CreateSterilizationLoadRequest request,
            Authentication authentication) {
        return createLoad.execute(request, authentication.getName());
    }

    @GetMapping("/sterilization-loads")
    @PreAuthorize("hasAnyRole('OPERATOR','SUPERVISOR','ADMIN')")
    public Page<SterilizationLoadResponse> listLoads(
            @RequestParam(required = false) LoadStatus status,
            @RequestParam(required = false) SterilizationMethod method,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @PageableDefault(size = 20) Pageable pageable) {
        return listLoads.execute(status, method, dateFrom, dateTo, pageable);
    }

    @GetMapping("/sterilization-loads/pending-orders")
    @PreAuthorize("hasAnyRole('SUPERVISOR','ADMIN')")
    public List<PendingOrderForLoadResponse> getPendingOrders() {
        return getPendingOrders.execute();
    }

    @GetMapping("/sterilization-loads/{id}")
    @PreAuthorize("hasAnyRole('OPERATOR','SUPERVISOR','ADMIN')")
    public SterilizationLoadDetailResponse getLoadDetail(@PathVariable UUID id) {
        return getLoadDetail.execute(id);
    }

    @PostMapping("/sterilization-loads/{id}/orders")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('SUPERVISOR','ADMIN')")
    public void addOrder(
            @PathVariable UUID id,
            @Valid @RequestBody AddOrderToLoadRequest request,
            Authentication authentication) {
        addOrderToLoad.execute(id, request.productionOrderId(), authentication.getName());
    }

    @DeleteMapping("/sterilization-loads/{id}/orders/{orderId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('SUPERVISOR','ADMIN')")
    public void removeOrder(@PathVariable UUID id, @PathVariable UUID orderId) {
        removeOrderFromLoad.execute(id, orderId);
    }

    @PutMapping("/sterilization-loads/{id}/status")
    @PreAuthorize("hasAnyRole('SUPERVISOR','ADMIN')")
    public SterilizationLoadResponse transitionStatus(
            @PathVariable UUID id,
            @Valid @RequestBody TransitionLoadStatusRequest request,
            Authentication authentication) {
        return transitionLoadStatus.execute(id, request.targetStatus(), authentication.getName());
    }

    // ===== MRP endpoints (US-085 / ADR-030, ADR-043) =====

    @PostMapping("/mrp/dry-run")
    @PreAuthorize("hasAnyRole('SUPERVISOR','ADMIN')")
    public com.industrialhub.backend.production.application.dto.MrpRunResult dryRunMrp(
            Authentication authentication) {
        return dryRunMrp.execute(authentication.getName());
    }

    @PostMapping("/mrp/run")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('SUPERVISOR','ADMIN')")
    public com.industrialhub.backend.production.application.dto.MrpRunResult runMrp(
            Authentication authentication) {
        return runMrp.execute(authentication.getName());
    }

    @GetMapping("/mrp/runs")
    @PreAuthorize("hasAnyRole('SUPERVISOR','ADMIN')")
    public Page<com.industrialhub.backend.production.application.dto.MrpRunResponse> getMrpRuns(
            @PageableDefault(size = 20) Pageable pageable) {
        return getMrpRuns.execute(pageable);
    }

    @GetMapping("/mrp/suggested-orders")
    @PreAuthorize("hasAnyRole('SUPERVISOR','ADMIN')")
    public List<com.industrialhub.backend.production.application.dto.MrpPlannedOrderResponse> getMrpSuggestions() {
        return getMrpSuggestions.execute();
    }

    @PutMapping("/mrp/suggested-orders/{id}/accept")
    @PreAuthorize("hasAnyRole('SUPERVISOR','ADMIN')")
    public com.industrialhub.backend.production.application.dto.MrpPlannedOrderResponse acceptSuggestion(
            @PathVariable UUID id,
            @RequestBody(required = false) com.industrialhub.backend.production.application.dto.AcceptMrpSuggestionRequest request,
            Authentication authentication) {
        Integer adjustedQty = request != null ? request.adjustedQty() : null;
        return acceptMrpSuggestion.execute(id, adjustedQty, authentication.getName());
    }

    @PutMapping("/mrp/suggested-orders/{id}/reject")
    @PreAuthorize("hasAnyRole('SUPERVISOR','ADMIN')")
    public com.industrialhub.backend.production.application.dto.MrpPlannedOrderResponse rejectSuggestion(
            @PathVariable UUID id,
            @Valid @RequestBody com.industrialhub.backend.production.application.dto.RejectMrpSuggestionRequest request,
            Authentication authentication) {
        return rejectMrpSuggestion.execute(id, request.reason(), authentication.getName());
    }

    @PutMapping("/mrp/suggested-orders/{id}/convert")
    @PreAuthorize("hasAnyRole('SUPERVISOR','ADMIN')")
    public com.industrialhub.backend.production.application.dto.MrpPlannedOrderResponse convertSuggestion(
            @PathVariable UUID id,
            Authentication authentication) {
        return convertMrpSuggestion.execute(id, authentication.getName());
    }

    // ===== Staffing endpoints (US-086 / ADR-030) =====

    @GetMapping("/staffing-config")
    @PreAuthorize("hasAnyRole('OPERATOR','SUPERVISOR','ADMIN')")
    public com.industrialhub.backend.production.application.dto.StaffingConfigResponse getStaffingConfig() {
        return getStaffingConfig.execute();
    }

    @PutMapping("/staffing-config")
    @PreAuthorize("hasRole('ADMIN')")
    public com.industrialhub.backend.production.application.dto.StaffingConfigResponse updateStaffingConfig(
            @Valid @RequestBody com.industrialhub.backend.production.application.dto.UpdateStaffingConfigRequest request,
            Authentication authentication) {
        return updateStaffingConfig.execute(request, authentication.getName());
    }

    @PutMapping("/production-orders/{id}/staffing")
    @PreAuthorize("hasAnyRole('SUPERVISOR','ADMIN')")
    public com.industrialhub.backend.production.application.dto.ProductionOrderStaffingResponse updateOrderStaffing(
            @PathVariable UUID id,
            @Valid @RequestBody com.industrialhub.backend.production.application.dto.UpdateOrderStaffingRequest request) {
        return updateOrderStaffing.execute(id, request.plannedPeople());
    }

    @DeleteMapping("/production-orders/{id}/staffing")
    @PreAuthorize("hasAnyRole('SUPERVISOR','ADMIN')")
    public com.industrialhub.backend.production.application.dto.ProductionOrderStaffingResponse resetOrderStaffing(
            @PathVariable UUID id) {
        return resetOrderStaffing.execute(id);
    }

    // ===== Planning Board endpoints (US-087 / ADR-030) =====

    @GetMapping("/planning/families")
    @PreAuthorize("hasAnyRole('SUPERVISOR','ADMIN')")
    public List<com.industrialhub.backend.production.application.dto.FamilyPlanningBoardResponse> getPlanningBoard() {
        return getPlanningBoard.execute();
    }

    @GetMapping("/planning/timeline")
    @PreAuthorize("hasAnyRole('SUPERVISOR','ADMIN')")
    public List<com.industrialhub.backend.production.application.dto.TimelineEntryResponse> getPlanningTimeline(
            @RequestParam(required = false) String familyCode,
            @RequestParam(defaultValue = "8") int weeks) {
        return getPlanningTimeline.execute(familyCode, weeks);
    }

    @GetMapping("/planning/purchase-needs")
    @PreAuthorize("hasAnyRole('SUPERVISOR','ADMIN')")
    public List<com.industrialhub.backend.production.application.dto.PurchaseNeedResponse> getPurchaseNeeds() {
        return getPurchaseNeeds.execute();
    }
}
