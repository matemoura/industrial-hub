package com.industrialhub.backend.common.application.usecase;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.industrialhub.backend.common.application.AuditService;
import com.industrialhub.backend.common.application.dto.SaveDashboardConfigRequest;
import com.industrialhub.backend.common.application.dto.UserDashboardConfigResponse;
import com.industrialhub.backend.common.domain.AuditAction;
import com.industrialhub.backend.common.domain.UserDashboardConfig;
import com.industrialhub.backend.common.infrastructure.UserDashboardConfigRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class SaveDashboardConfigUseCase {

    private final UserDashboardConfigRepository repository;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public SaveDashboardConfigUseCase(UserDashboardConfigRepository repository,
                                       AuditService auditService,
                                       ObjectMapper objectMapper) {
        this.repository = repository;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public UserDashboardConfigResponse execute(String username, SaveDashboardConfigRequest request) {
        // SEC-102: validate widgetsJson is valid JSON before persisting
        try {
            objectMapper.readTree(request.widgetsJson());
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("widgetsJson contém JSON inválido");
        }

        UserDashboardConfig config = repository.findByUsername(username)
                .orElseGet(() -> UserDashboardConfig.builder()
                        .username(username)
                        .updatedAt(LocalDateTime.now())
                        .build());

        config.setWidgetsJson(request.widgetsJson());
        config.setUpdatedAt(LocalDateTime.now());

        UserDashboardConfig saved = repository.save(config);

        // SEC-104: audit dashboard config save
        auditService.log(username, AuditAction.DASHBOARD_CONFIG_SAVED, "UserDashboardConfig",
                saved.getId() != null ? saved.getId().toString() : username,
                java.util.Map.of("widgetsJsonLength", request.widgetsJson().length()));

        return new UserDashboardConfigResponse(saved.getWidgetsJson());
    }
}
