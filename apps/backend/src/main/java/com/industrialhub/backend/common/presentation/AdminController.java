package com.industrialhub.backend.common.presentation;

import com.industrialhub.backend.common.application.dto.AuditLogResponse;
import com.industrialhub.backend.common.application.usecase.GetAuditLogUseCase;
import com.industrialhub.backend.common.domain.AuditAction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    private final GetAuditLogUseCase getAuditLogUseCase;

    public AdminController(GetAuditLogUseCase getAuditLogUseCase) {
        this.getAuditLogUseCase = getAuditLogUseCase;
    }

    @GetMapping("/audit-log")
    @PreAuthorize("hasRole('ADMIN')")
    public Page<AuditLogResponse> getAuditLog(
        @RequestParam(required = false) String entityType,
        @RequestParam(required = false) AuditAction action,
        @RequestParam(required = false) String username,
        @PageableDefault(size = 50) Pageable pageable
    ) {
        return getAuditLogUseCase.execute(entityType, action, username, pageable);
    }
}
