package com.industrialhub.backend.common.application.usecase;

import com.industrialhub.backend.common.infrastructure.UserDashboardConfigRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeleteDashboardConfigUseCase {

    private final UserDashboardConfigRepository repository;

    public DeleteDashboardConfigUseCase(UserDashboardConfigRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void execute(String username) {
        repository.deleteByUsername(username);
    }
}
