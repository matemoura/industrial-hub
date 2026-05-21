package com.industrialhub.backend.common.presentation;

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
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/sla-rules")
@RequiredArgsConstructor
public class SlaRuleController {

    private final CreateSlaRuleUseCase createSlaRule;
    private final GetSlaRuleListUseCase getSlaRuleList;
    private final UpdateSlaRuleUseCase updateSlaRule;
    private final DeleteSlaRuleUseCase deleteSlaRule;
    private final EscalationUseCase escalationUseCase;
    private final GetSlaSummaryUseCase getSlaSummary;

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
    public EscalationRunResponse runNow() {
        return escalationUseCase.execute();
    }

    @GetMapping("/summary")
    @PreAuthorize("hasRole('ADMIN')")
    public SlaSummaryResponse summary() {
        return getSlaSummary.execute();
    }
}
