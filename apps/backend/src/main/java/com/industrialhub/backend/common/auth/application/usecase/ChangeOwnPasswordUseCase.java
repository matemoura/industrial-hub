package com.industrialhub.backend.common.auth.application.usecase;

import com.industrialhub.backend.common.application.AuditService;
import com.industrialhub.backend.common.auth.application.dto.ChangePasswordRequest;
import com.industrialhub.backend.common.auth.application.dto.LoginResponseDto;
import com.industrialhub.backend.common.auth.domain.InvalidPasswordException;
import com.industrialhub.backend.common.auth.infrastructure.UserRepository;
import com.industrialhub.backend.common.domain.AuditAction;
import com.industrialhub.backend.common.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChangeOwnPasswordUseCase {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuditService auditService;

    public ChangeOwnPasswordUseCase(UserRepository userRepository,
                                    PasswordEncoder passwordEncoder,
                                    JwtService jwtService,
                                    AuditService auditService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.auditService = auditService;
    }

    @Transactional
    public LoginResponseDto execute(String username, ChangePasswordRequest request) {
        var user = userRepository.findByUsername(username)
                .orElseThrow(() -> new InvalidPasswordException("Usuário não encontrado"));

        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            throw new InvalidPasswordException("Senha atual incorreta");
        }

        if (passwordEncoder.matches(request.newPassword(), user.getPassword())) {
            throw new InvalidPasswordException("A nova senha deve ser diferente da senha atual");
        }

        CreateUserUseCase.validatePasswordStrength(request.newPassword());

        user.setPassword(passwordEncoder.encode(request.newPassword()));
        user.setMustChangePassword(false);
        userRepository.save(user);

        auditService.log(username, AuditAction.PASSWORD_CHANGED, "User", username, (java.util.Map<String, Object>) null);

        String newToken = jwtService.generateToken(user.getUsername(), user.getRole().name(), false);
        return new LoginResponseDto(newToken, user.getUsername(), user.getRole().name(),
                jwtService.getExpirationMs(), false);
    }
}
