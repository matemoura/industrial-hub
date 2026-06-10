package com.industrialhub.backend.common.auth.application.usecase;

import com.industrialhub.backend.common.application.AuditService;
import com.industrialhub.backend.common.auth.application.dto.LoginRequestDto;
import com.industrialhub.backend.common.auth.application.dto.LoginResponseDto;
import com.industrialhub.backend.common.auth.domain.Role;
import com.industrialhub.backend.common.auth.domain.User;
import com.industrialhub.backend.common.auth.infrastructure.UserRepository;
import com.industrialhub.backend.common.security.JwtService;
import com.industrialhub.backend.common.security.LoginRateLimiter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoginWithEmailUseCaseTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;
    @Mock private LoginRateLimiter rateLimiter;
    @Mock private AuditService auditService;

    @InjectMocks
    private LoginUseCase loginUseCase;

    @Test
    void loginComEmailValido_retornaJwtComSubUsername() {
        User user = User.builder()
                .id(UUID.randomUUID())
                .username("joao.silva")
                .email("joao.silva@msb.internal")
                .password("hashed")
                .role(Role.USER)
                .active(true)
                .build();
        when(userRepository.findByEmail("joao.silva@msb.internal")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Senha1234", "hashed")).thenReturn(true);
        when(jwtService.generateToken("joao.silva", "USER", false)).thenReturn("jwt-abc");
        when(jwtService.getExpirationMs()).thenReturn(28800000L);

        LoginResponseDto response = loginUseCase.execute(
                new LoginRequestDto("joao.silva@msb.internal", "Senha1234"), "10.0.0.1");

        assertThat(response.token()).isEqualTo("jwt-abc");
        assertThat(response.username()).isEqualTo("joao.silva");
        assertThat(response.email()).isEqualTo("joao.silva@msb.internal");
        assertThat(response.role()).isEqualTo("USER");
    }

    @Test
    void loginComEmailInexistente_lancaInvalidCredentials() {
        when(userRepository.findByEmail("naoexiste@msb.internal")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> loginUseCase.execute(
                new LoginRequestDto("naoexiste@msb.internal", "qualquer"), "10.0.0.1"))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void loginComRoleUser_retornaRoleUserNoToken() {
        User user = User.builder()
                .id(UUID.randomUUID())
                .username("ana.costa")
                .email("ana.costa@msb.internal")
                .password("hashed")
                .role(Role.USER)
                .active(true)
                .build();
        when(userRepository.findByEmail("ana.costa@msb.internal")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Senha9999", "hashed")).thenReturn(true);
        when(jwtService.generateToken("ana.costa", "USER", false)).thenReturn("jwt-user");
        when(jwtService.getExpirationMs()).thenReturn(28800000L);

        LoginResponseDto response = loginUseCase.execute(
                new LoginRequestDto("ana.costa@msb.internal", "Senha9999"), "10.0.0.1");

        assertThat(response.role()).isEqualTo("USER");
    }
}
