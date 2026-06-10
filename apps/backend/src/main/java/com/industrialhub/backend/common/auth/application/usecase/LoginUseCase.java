package com.industrialhub.backend.common.auth.application.usecase;

import com.industrialhub.backend.common.application.AuditService;
import com.industrialhub.backend.common.auth.application.dto.LoginRequestDto;
import com.industrialhub.backend.common.auth.application.dto.LoginResponseDto;
import com.industrialhub.backend.common.auth.domain.User;
import com.industrialhub.backend.common.auth.infrastructure.UserRepository;
import com.industrialhub.backend.common.domain.AuditAction;
import com.industrialhub.backend.common.security.JwtService;
import com.industrialhub.backend.common.security.LoginRateLimiter;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class LoginUseCase {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final LoginRateLimiter rateLimiter;
    private final AuditService auditService;

    public LoginUseCase(UserRepository userRepository,
                        PasswordEncoder passwordEncoder,
                        JwtService jwtService,
                        LoginRateLimiter rateLimiter,
                        AuditService auditService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.rateLimiter = rateLimiter;
        this.auditService = auditService;
    }

    public LoginResponseDto execute(LoginRequestDto request, String ipAddress) {
        rateLimiter.checkLimit(ipAddress, request.email());

        try {
            User user = userRepository.findByEmail(request.email())
                    .orElseThrow(InvalidCredentialsException::new);

            if (!user.isActive()) {
                throw new InvalidCredentialsException();
            }

            if (!passwordEncoder.matches(request.password(), user.getPassword())) {
                throw new InvalidCredentialsException();
            }

            String token = jwtService.generateToken(
                    user.getUsername(), user.getRole().name(), user.isMustChangePassword());

            return new LoginResponseDto(
                    token,
                    user.getUsername(),
                    user.getRole().name(),
                    user.getEmail(),
                    jwtService.getExpirationMs(),
                    user.isMustChangePassword()
            );
        } catch (InvalidCredentialsException ex) {
            auditService.logWithIp(
                    request.email(),
                    AuditAction.LOGIN_FAILED,
                    "Auth",
                    request.email(),
                    null,
                    ipAddress
            );
            throw ex;
        }
    }
}
