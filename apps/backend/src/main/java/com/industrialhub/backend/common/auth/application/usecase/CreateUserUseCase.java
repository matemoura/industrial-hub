package com.industrialhub.backend.common.auth.application.usecase;

import com.industrialhub.backend.common.application.AuditService;
import com.industrialhub.backend.common.auth.application.dto.CreateUserRequest;
import com.industrialhub.backend.common.auth.application.dto.UserResponse;
import com.industrialhub.backend.common.auth.domain.InvalidPasswordException;
import com.industrialhub.backend.common.auth.domain.User;
import com.industrialhub.backend.common.auth.domain.UserAlreadyExistsException;
import com.industrialhub.backend.common.auth.infrastructure.UserRepository;
import com.industrialhub.backend.common.domain.AuditAction;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class CreateUserUseCase {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public CreateUserUseCase(UserRepository userRepository,
                             PasswordEncoder passwordEncoder,
                             AuditService auditService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    public UserResponse execute(CreateUserRequest request, String adminUsername) {
        if (userRepository.existsByUsername(request.username())) {
            throw new UserAlreadyExistsException(request.username());
        }

        validatePasswordStrength(request.temporaryPassword());

        User user = User.builder()
                .username(request.username())
                .email(request.email())
                .role(request.role())
                .password(passwordEncoder.encode(request.temporaryPassword()))
                .active(true)
                .mustChangePassword(true)
                .build();

        User saved = userRepository.save(user);

        auditService.log(adminUsername, AuditAction.USER_CREATED, "User",
                saved.getId(), Map.of("role", saved.getRole().name()));

        return UserResponse.from(saved);
    }

    static void validatePasswordStrength(String password) {
        if (password.length() < 8
                || !password.chars().anyMatch(Character::isUpperCase)
                || !password.chars().anyMatch(Character::isDigit)) {
            throw new InvalidPasswordException(
                    "Senha deve ter no mínimo 8 caracteres, 1 maiúscula e 1 dígito");
        }
    }
}
