package com.industrialhub.backend.common.application.usecase;

import com.industrialhub.backend.common.application.AuditService;
import com.industrialhub.backend.common.domain.AuditAction;
import com.industrialhub.backend.common.infrastructure.UserDashboardConfigRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
public class DeleteDashboardConfigUseCase {

    private final UserDashboardConfigRepository repository;
    private final AuditService auditService;

    public DeleteDashboardConfigUseCase(UserDashboardConfigRepository repository,
                                         AuditService auditService) {
        this.repository = repository;
        this.auditService = auditService;
    }

    @Transactional
    public void execute(String username) {
        // SEC-104: audit only when config existed (idempotent — no log when nothing to delete)
        boolean existed = repository.findByUsername(username).isPresent();
        repository.deleteByUsername(username);
        if (existed) {
            auditService.log(username, AuditAction.DASHBOARD_CONFIG_RESET, "UserDashboardConfig",
                    username, Map.of());
        }
    }
}
