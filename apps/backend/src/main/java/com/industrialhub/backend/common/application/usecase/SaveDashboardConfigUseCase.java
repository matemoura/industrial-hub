package com.industrialhub.backend.common.application.usecase;

import com.industrialhub.backend.common.application.dto.SaveDashboardConfigRequest;
import com.industrialhub.backend.common.application.dto.UserDashboardConfigResponse;
import com.industrialhub.backend.common.domain.UserDashboardConfig;
import com.industrialhub.backend.common.infrastructure.UserDashboardConfigRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class SaveDashboardConfigUseCase {

    private final UserDashboardConfigRepository repository;

    public SaveDashboardConfigUseCase(UserDashboardConfigRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public UserDashboardConfigResponse execute(String username, SaveDashboardConfigRequest request) {
        UserDashboardConfig config = repository.findByUsername(username)
                .orElseGet(() -> UserDashboardConfig.builder()
                        .username(username)
                        .updatedAt(LocalDateTime.now())
                        .build());

        config.setWidgetsJson(request.widgetsJson());
        config.setUpdatedAt(LocalDateTime.now());

        UserDashboardConfig saved = repository.save(config);
        return new UserDashboardConfigResponse(saved.getWidgetsJson());
    }
}
