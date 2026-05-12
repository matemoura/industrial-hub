package com.industrialhub.backend.common.auth.application.usecase;

import com.industrialhub.backend.common.auth.application.dto.LoginRequestDto;
import com.industrialhub.backend.common.auth.application.dto.LoginResponseDto;
import com.industrialhub.backend.common.auth.domain.User;
import com.industrialhub.backend.common.auth.infrastructure.UserRepository;
import com.industrialhub.backend.common.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class LoginUseCase {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public LoginUseCase(UserRepository userRepository,
                        PasswordEncoder passwordEncoder,
                        JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public LoginResponseDto execute(LoginRequestDto request) {
        User user = userRepository.findByUsername(request.username())
                .orElseThrow(InvalidCredentialsException::new);

        if (!user.isActive()) {
            throw new InvalidCredentialsException();
        }

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new InvalidCredentialsException();
        }

        String token = jwtService.generateToken(user.getUsername(), user.getRole().name());

        return new LoginResponseDto(
                token,
                user.getUsername(),
                user.getRole().name(),
                jwtService.getExpirationMs()
        );
    }
}
