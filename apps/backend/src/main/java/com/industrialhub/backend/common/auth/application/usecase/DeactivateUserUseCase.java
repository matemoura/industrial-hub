package com.industrialhub.backend.common.auth.application.usecase;

import com.industrialhub.backend.common.application.AuditService;
import com.industrialhub.backend.common.auth.domain.LastAdminException;
import com.industrialhub.backend.common.auth.domain.Role;
import com.industrialhub.backend.common.auth.domain.UserNotFoundException;
import com.industrialhub.backend.common.auth.infrastructure.UserRepository;
import com.industrialhub.backend.common.domain.AuditAction;
import org.springframework.stereotype.Service;

import java.util.Map;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class DeactivateUserUseCase {

    private final UserRepository userRepository;
    private final AuditService auditService;

    public DeactivateUserUseCase(UserRepository userRepository, AuditService auditService) {
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    @Transactional
    public void execute(UUID userId, String adminUsername) {
        var user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        if (user.getRole() == Role.ADMIN && userRepository.countByRoleAndActiveTrue(Role.ADMIN) <= 1) {
            throw new LastAdminException();
        }

        user.setActive(false);
        userRepository.save(user);

        auditService.log(adminUsername, AuditAction.USER_DEACTIVATED, "User",
                userId, Map.of("username", user.getUsername()));
    }
}
