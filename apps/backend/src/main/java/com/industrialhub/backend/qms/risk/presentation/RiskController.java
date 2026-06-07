package com.industrialhub.backend.qms.risk.presentation;

import com.industrialhub.backend.qms.risk.application.dto.CreateMitigationActionRequest;
import com.industrialhub.backend.qms.risk.application.dto.CreateRiskItemRequest;
import com.industrialhub.backend.qms.risk.application.dto.MitigationActionResponse;
import com.industrialhub.backend.qms.risk.application.dto.RiskItemDetailResponse;
import com.industrialhub.backend.qms.risk.application.dto.RiskItemResponse;
import com.industrialhub.backend.qms.risk.application.dto.RiskMatrixResponse;
import com.industrialhub.backend.qms.risk.application.dto.RiskSummary;
import com.industrialhub.backend.qms.risk.application.dto.UpdateMitigationActionRequest;
import com.industrialhub.backend.qms.risk.application.dto.UpdateRiskItemRequest;
import com.industrialhub.backend.qms.risk.application.dto.UpdateRiskStatusRequest;
import com.industrialhub.backend.qms.risk.application.usecase.CreateMitigationActionUseCase;
import com.industrialhub.backend.qms.risk.application.usecase.CreateRiskItemUseCase;
import com.industrialhub.backend.qms.risk.application.usecase.GetRiskItemDetailUseCase;
import com.industrialhub.backend.qms.risk.application.usecase.GetRiskItemsUseCase;
import com.industrialhub.backend.qms.risk.application.usecase.GetRiskMatrixUseCase;
import com.industrialhub.backend.qms.risk.application.usecase.GetRiskSummaryUseCase;
import com.industrialhub.backend.qms.risk.application.usecase.TransitionRiskStatusUseCase;
import com.industrialhub.backend.qms.risk.application.usecase.UpdateMitigationActionUseCase;
import com.industrialhub.backend.qms.risk.application.usecase.UpdateRiskItemUseCase;
import com.industrialhub.backend.qms.risk.domain.RiskLevel;
import com.industrialhub.backend.qms.risk.domain.RiskStatus;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/qms/risks")
@Validated
public class RiskController {

    private final CreateRiskItemUseCase createRiskItem;
    private final UpdateRiskItemUseCase updateRiskItem;
    private final TransitionRiskStatusUseCase transitionStatus;
    private final GetRiskItemsUseCase getRiskItems;
    private final GetRiskItemDetailUseCase getRiskItemDetail;
    private final CreateMitigationActionUseCase createMitigationAction;
    private final UpdateMitigationActionUseCase updateMitigationAction;
    private final GetRiskMatrixUseCase getRiskMatrix;
    private final GetRiskSummaryUseCase getRiskSummary;

    public RiskController(CreateRiskItemUseCase createRiskItem,
                          UpdateRiskItemUseCase updateRiskItem,
                          TransitionRiskStatusUseCase transitionStatus,
                          GetRiskItemsUseCase getRiskItems,
                          GetRiskItemDetailUseCase getRiskItemDetail,
                          CreateMitigationActionUseCase createMitigationAction,
                          UpdateMitigationActionUseCase updateMitigationAction,
                          GetRiskMatrixUseCase getRiskMatrix,
                          GetRiskSummaryUseCase getRiskSummary) {
        this.createRiskItem = createRiskItem;
        this.updateRiskItem = updateRiskItem;
        this.transitionStatus = transitionStatus;
        this.getRiskItems = getRiskItems;
        this.getRiskItemDetail = getRiskItemDetail;
        this.createMitigationAction = createMitigationAction;
        this.updateMitigationAction = updateMitigationAction;
        this.getRiskMatrix = getRiskMatrix;
        this.getRiskSummary = getRiskSummary;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'ADMIN')")
    public ResponseEntity<RiskItemResponse> create(
            @RequestBody @Valid CreateRiskItemRequest request,
            Principal principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(createRiskItem.execute(request, principal.getName()));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('OPERATOR', 'SUPERVISOR', 'ADMIN')")
    public ResponseEntity<Page<RiskItemResponse>> list(
            @RequestParam(required = false) RiskStatus status,
            @RequestParam(required = false) RiskLevel riskLevel,
            @RequestParam(required = false) String owner,
            @RequestParam(required = false) UUID linkedNcId,
            @RequestParam(required = false) String linkedProductCode,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(getRiskItems.execute(status, riskLevel, owner, linkedNcId, linkedProductCode, pageable));
    }

    @GetMapping("/matrix")
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'ADMIN')")
    public ResponseEntity<RiskMatrixResponse> getMatrix() {
        return ResponseEntity.ok(getRiskMatrix.execute());
    }

    @GetMapping("/summary")
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'ADMIN')")
    public ResponseEntity<RiskSummary> getSummary() {
        return ResponseEntity.ok(getRiskSummary.execute());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('OPERATOR', 'SUPERVISOR', 'ADMIN')")
    public ResponseEntity<RiskItemDetailResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(getRiskItemDetail.execute(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'ADMIN')")
    public ResponseEntity<RiskItemResponse> update(
            @PathVariable UUID id,
            @RequestBody @Valid UpdateRiskItemRequest request) {
        return ResponseEntity.ok(updateRiskItem.execute(id, request));
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'ADMIN')")
    public ResponseEntity<RiskItemResponse> transitionStatus(
            @PathVariable UUID id,
            @RequestBody @Valid UpdateRiskStatusRequest request,
            Principal principal) {
        return ResponseEntity.ok(transitionStatus.execute(id, request, principal.getName()));
    }

    @PostMapping("/{id}/mitigation-actions")
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'ADMIN')")
    public ResponseEntity<MitigationActionResponse> createMitigationAction(
            @PathVariable UUID id,
            @RequestBody @Valid CreateMitigationActionRequest request,
            Principal principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(createMitigationAction.execute(id, request, principal.getName()));
    }

    @PutMapping("/{id}/mitigation-actions/{actionId}")
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'ADMIN')")
    public ResponseEntity<MitigationActionResponse> updateMitigationAction(
            @PathVariable UUID id,
            @PathVariable UUID actionId,
            @RequestBody @Valid UpdateMitigationActionRequest request,
            Principal principal) {
        return ResponseEntity.ok(updateMitigationAction.execute(actionId, request, principal.getName()));
    }
}
