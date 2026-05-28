package com.industrialhub.backend.common.presentation;

import com.industrialhub.backend.common.application.AuditService;
import com.industrialhub.backend.common.application.dto.AlertThresholdResponse;
import com.industrialhub.backend.common.application.dto.CreateAlertThresholdRequest;
import com.industrialhub.backend.common.application.dto.UpdateAlertThresholdRequest;
import com.industrialhub.backend.common.application.usecase.AlertEvaluatorUseCase;
import com.industrialhub.backend.common.application.usecase.CreateAlertThresholdUseCase;
import com.industrialhub.backend.common.application.usecase.DeleteAlertThresholdUseCase;
import com.industrialhub.backend.common.application.usecase.GetAlertThresholdsUseCase;
import com.industrialhub.backend.common.application.usecase.UpdateAlertThresholdUseCase;
import com.industrialhub.backend.common.domain.AuditAction;
import com.industrialhub.backend.common.domain.EvaluateNowCooldownException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

@RestController
@RequestMapping("/api/v1/admin/alert-thresholds")
@PreAuthorize("hasRole('ADMIN')")
@Validated
public class AlertThresholdController {

    private static final Duration EVALUATE_NOW_COOLDOWN = Duration.ofMinutes(5);

    private final AtomicReference<Instant> lastManualEvaluation = new AtomicReference<>(Instant.EPOCH);

    private final CreateAlertThresholdUseCase createUseCase;
    private final GetAlertThresholdsUseCase getUseCase;
    private final UpdateAlertThresholdUseCase updateUseCase;
    private final DeleteAlertThresholdUseCase deleteUseCase;
    private final AlertEvaluatorUseCase alertEvaluatorUseCase;
    private final AuditService auditService;

    public AlertThresholdController(CreateAlertThresholdUseCase createUseCase,
                                    GetAlertThresholdsUseCase getUseCase,
                                    UpdateAlertThresholdUseCase updateUseCase,
                                    DeleteAlertThresholdUseCase deleteUseCase,
                                    AlertEvaluatorUseCase alertEvaluatorUseCase,
                                    AuditService auditService) {
        this.createUseCase = createUseCase;
        this.getUseCase = getUseCase;
        this.updateUseCase = updateUseCase;
        this.deleteUseCase = deleteUseCase;
        this.alertEvaluatorUseCase = alertEvaluatorUseCase;
        this.auditService = auditService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPERVISOR','ADMIN')")
    public ResponseEntity<AlertThresholdResponse> create(
            @Valid @RequestBody CreateAlertThresholdRequest request,
            Principal principal) {
        AlertThresholdResponse response = createUseCase.execute(request, principal.getName()); // SEC-069: @PreAuthorize guarantees non-null
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
    @PreAuthorize("hasAnyRole('SUPERVISOR','ADMIN')")
    public ResponseEntity<Map<String, Integer>> evaluateNow(Principal principal) {
        Instant now = Instant.now();
        Instant last = lastManualEvaluation.get();

        if (Duration.between(last, now).compareTo(EVALUATE_NOW_COOLDOWN) < 0) {
            long remaining = EVALUATE_NOW_COOLDOWN.toSeconds() - Duration.between(last, now).toSeconds();
            throw new EvaluateNowCooldownException(remaining);
        }

        if (!lastManualEvaluation.compareAndSet(last, now)) {
            Instant updatedLast = lastManualEvaluation.get();
            long remaining = EVALUATE_NOW_COOLDOWN.toSeconds() - Duration.between(updatedLast, now).toSeconds();
            if (remaining > 0) {
                throw new EvaluateNowCooldownException(remaining);
            }
        }

        int evaluated = alertEvaluatorUseCase.execute();
        auditService.log(
                principal.getName(), // SEC-069: @PreAuthorize guarantees non-null
                AuditAction.ALERT_EVALUATED_MANUAL,
                "AlertThreshold",
                "all",
                Map.of("evaluated", String.valueOf(evaluated)));
        return ResponseEntity.ok(Map.of("evaluated", evaluated));
    }
}
