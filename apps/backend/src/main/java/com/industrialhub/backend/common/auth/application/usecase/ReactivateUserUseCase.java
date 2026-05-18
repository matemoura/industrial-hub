package com.industrialhub.backend.common.auth.application.usecase;

import com.industrialhub.backend.common.application.AuditService;
import com.industrialhub.backend.common.auth.domain.UserNotFoundException;
import com.industrialhub.backend.common.auth.infrastructure.UserRepository;
import com.industrialhub.backend.common.domain.AuditAction;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
public class ReactivateUserUseCase {

    private final UserRepository userRepository;
    private final AuditService auditService;

    public ReactivateUserUseCase(UserRepository userRepository, AuditService auditService) {
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    @Transactional
    public void execute(UUID userId, String adminUsername) {
        var user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
        user.setActive(true);
        userRepository.save(user);

        auditService.log(adminUsername, AuditAction.USER_REACTIVATED, "User", userId, Map.of());
    }
}
