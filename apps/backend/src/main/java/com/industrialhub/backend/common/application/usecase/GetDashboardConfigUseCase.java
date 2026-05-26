package com.industrialhub.backend.common.application.usecase;

import com.industrialhub.backend.common.application.DashboardDefaultLayouts;
import com.industrialhub.backend.common.application.dto.UserDashboardConfigResponse;
import com.industrialhub.backend.common.infrastructure.UserDashboardConfigRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GetDashboardConfigUseCase {

    private final UserDashboardConfigRepository repository;

    public GetDashboardConfigUseCase(UserDashboardConfigRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public UserDashboardConfigResponse execute(String username, String role) {
        return repository.findByUsername(username)
                .map(config -> new UserDashboardConfigResponse(config.getWidgetsJson()))
                .orElseGet(() -> new UserDashboardConfigResponse(DashboardDefaultLayouts.forRole(role)));
    }
}
