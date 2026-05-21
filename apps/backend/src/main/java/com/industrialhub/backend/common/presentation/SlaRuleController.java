package com.industrialhub.backend.common.presentation;

import com.industrialhub.backend.common.application.AuditService;
import com.industrialhub.backend.common.application.dto.CreateSlaRuleRequest;
import com.industrialhub.backend.common.application.dto.EscalationRunResponse;
import com.industrialhub.backend.common.application.dto.SlaSummaryResponse;
import com.industrialhub.backend.common.application.dto.SlaRuleResponse;
import com.industrialhub.backend.common.application.dto.UpdateSlaRuleRequest;
import com.industrialhub.backend.common.application.usecase.CreateSlaRuleUseCase;
import com.industrialhub.backend.common.application.usecase.DeleteSlaRuleUseCase;
import com.industrialhub.backend.common.application.usecase.EscalationUseCase;
import com.industrialhub.backend.common.application.usecase.GetSlaSummaryUseCase;
import com.industrialhub.backend.common.application.usecase.GetSlaRuleListUseCase;
import com.industrialhub.backend.common.application.usecase.UpdateSlaRuleUseCase;
import com.industrialhub.backend.common.domain.AuditAction;
import com.industrialhub.backend.common.domain.EscalationCooldownException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

@RestController
@RequestMapping("/api/v1/admin/sla-rules")
@RequiredArgsConstructor
public class SlaRuleController {

    private static final Duration RUN_NOW_COOLDOWN = Duration.ofMinutes(5);

    private final AtomicReference<Instant> lastManualEscalation = new AtomicReference<>(Instant.EPOCH);

    private final CreateSlaRuleUseCase createSlaRule;
    private final GetSlaRuleListUseCase getSlaRuleList;
    private final UpdateSlaRuleUseCase updateSlaRule;
    private final DeleteSlaRuleUseCase deleteSlaRule;
    private final EscalationUseCase escalationUseCase;
    private final GetSlaSummaryUseCase getSlaSummary;
    private final AuditService auditService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<SlaRuleResponse> list() {
        return getSlaRuleList.execute();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public SlaRuleResponse create(@Valid @RequestBody CreateSlaRuleRequest request,
                                   Principal principal) {
        return createSlaRule.execute(request, principal.getName());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public SlaRuleResponse update(@PathVariable UUID id,
                                   @Valid @RequestBody UpdateSlaRuleRequest request,
                                   Principal principal) {
        return updateSlaRule.execute(id, request, principal.getName());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable UUID id, Principal principal) {
        deleteSlaRule.execute(id, principal.getName());
    }

    @PostMapping("/run-now")
    @PreAuthorize("hasRole('ADMIN')")
    public EscalationRunResponse runNow(Principal principal) {
        Instant now = Instant.now();
        Instant last = lastManualEscalation.get();

        if (Duration.between(last, now).compareTo(RUN_NOW_COOLDOWN) < 0) {
            long remaining = RUN_NOW_COOLDOWN.toSeconds() - Duration.between(last, now).toSeconds();
            throw new EscalationCooldownException(remaining);
        }

        if (!lastManualEscalation.compareAndSet(last, now)) {
            Instant updatedLast = lastManualEscalation.get();
            long remaining = RUN_NOW_COOLDOWN.toSeconds() - Duration.between(updatedLast, now).toSeconds();
            if (remaining > 0) {
                throw new EscalationCooldownException(remaining);
            }
        }

        String username = principal != null ? principal.getName() : "system";
        EscalationRunResponse result = escalationUseCase.execute(username);

        auditService.log(
            username,
            AuditAction.ESCALATION_RUN_MANUAL,
            "SlaRule",
            "all",
            Map.of("breachedNcs",        String.valueOf(result.breachedNcs()),
                   "breachedWorkOrders", String.valueOf(result.breachedWorkOrders()))
        );

        return result;
    }

    @GetMapping("/summary")
    @PreAuthorize("hasRole('ADMIN')")
    public SlaSummaryResponse summary() {
        return getSlaSummary.execute();
    }
}
