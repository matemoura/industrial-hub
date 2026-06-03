package com.industrialhub.backend.qms.presentation;

import com.industrialhub.backend.qms.application.dto.CAPASummaryResponse;
import com.industrialhub.backend.qms.application.usecase.ListCAPAsUseCase;
import com.industrialhub.backend.qms.domain.ActionStatus;
import com.industrialhub.backend.qms.domain.ActionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/qms/capas")
@Validated
public class CapaController {

    private final ListCAPAsUseCase listCAPAs;

    public CapaController(ListCAPAsUseCase listCAPAs) {
        this.listCAPAs = listCAPAs;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'ADMIN')")
    public Page<CAPASummaryResponse> listCapas(
            @RequestParam(required = false) ActionType type,
            @RequestParam(required = false) ActionStatus status,
            @RequestParam(required = false) UUID ncId,
            @PageableDefault(size = 20) Pageable pageable) {
        return listCAPAs.execute(type, status, ncId, pageable);
    }
}
