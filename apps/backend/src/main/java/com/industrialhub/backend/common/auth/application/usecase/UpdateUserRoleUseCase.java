package com.industrialhub.backend.common.auth.application.usecase;

import com.industrialhub.backend.common.application.AuditService;
import com.industrialhub.backend.common.auth.application.dto.UpdateUserRoleRequest;
import com.industrialhub.backend.common.auth.application.dto.UserResponse;
import com.industrialhub.backend.common.auth.domain.UserNotFoundException;
import com.industrialhub.backend.common.auth.infrastructure.UserRepository;
import com.industrialhub.backend.common.domain.AuditAction;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
public class UpdateUserRoleUseCase {

    private final UserRepository userRepository;
    private final AuditService auditService;

    public UpdateUserRoleUseCase(UserRepository userRepository, AuditService auditService) {
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    @Transactional
    public UserResponse execute(UUID userId, UpdateUserRoleRequest request, String adminUsername) {
        var user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
        user.setRole(request.role());
        var saved = userRepository.save(user);

        auditService.log(adminUsername, AuditAction.ROLE_CHANGED, "User", userId,
                Map.of("newRole", request.role().name()));

        return UserResponse.from(saved);
    }
}
