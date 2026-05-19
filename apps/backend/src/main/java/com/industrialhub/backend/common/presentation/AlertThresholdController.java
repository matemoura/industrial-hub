package com.industrialhub.backend.common.presentation;

import com.industrialhub.backend.common.application.dto.AlertThresholdResponse;
import com.industrialhub.backend.common.application.dto.CreateAlertThresholdRequest;
import com.industrialhub.backend.common.application.dto.UpdateAlertThresholdRequest;
import com.industrialhub.backend.common.application.usecase.AlertEvaluatorUseCase;
import com.industrialhub.backend.common.application.usecase.CreateAlertThresholdUseCase;
import com.industrialhub.backend.common.application.usecase.DeleteAlertThresholdUseCase;
import com.industrialhub.backend.common.application.usecase.GetAlertThresholdsUseCase;
import com.industrialhub.backend.common.application.usecase.UpdateAlertThresholdUseCase;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/alert-thresholds")
@PreAuthorize("hasRole('ADMIN')")
@Validated
public class AlertThresholdController {

    private final CreateAlertThresholdUseCase createUseCase;
    private final GetAlertThresholdsUseCase getUseCase;
    private final UpdateAlertThresholdUseCase updateUseCase;
    private final DeleteAlertThresholdUseCase deleteUseCase;
    private final AlertEvaluatorUseCase alertEvaluatorUseCase;

    public AlertThresholdController(CreateAlertThresholdUseCase createUseCase,
                                    GetAlertThresholdsUseCase getUseCase,
                                    UpdateAlertThresholdUseCase updateUseCase,
                                    DeleteAlertThresholdUseCase deleteUseCase,
                                    AlertEvaluatorUseCase alertEvaluatorUseCase) {
        this.createUseCase = createUseCase;
        this.getUseCase = getUseCase;
        this.updateUseCase = updateUseCase;
        this.deleteUseCase = deleteUseCase;
        this.alertEvaluatorUseCase = alertEvaluatorUseCase;
    }

    @PostMapping
    public ResponseEntity<AlertThresholdResponse> create(
            @Valid @RequestBody CreateAlertThresholdRequest request,
            Principal principal) {
        AlertThresholdResponse response = createUseCase.execute(request,
                principal != null ? principal.getName() : "system");
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<AlertThresholdResponse>> list() {
        return ResponseEntity.ok(getUseCase.execute());
    }

    @PutMapping("/{id}")
    public ResponseEntity<AlertThresholdResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateAlertThresholdRequest request) {
        return ResponseEntity.ok(updateUseCase.execute(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        deleteUseCase.execute(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/evaluate-now")
    public ResponseEntity<java.util.Map<String, Integer>> evaluateNow() {
        int evaluated = alertEvaluatorUseCase.execute();
        return ResponseEntity.ok(java.util.Map.of("evaluated", evaluated));
    }
}
