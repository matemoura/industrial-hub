package com.industrialhub.backend.common.auth.application.usecase;

import com.industrialhub.backend.common.application.AuditService;
import com.industrialhub.backend.common.auth.application.dto.LoginRequestDto;
import com.industrialhub.backend.common.auth.application.dto.LoginResponseDto;
import com.industrialhub.backend.common.auth.domain.Role;
import com.industrialhub.backend.common.auth.domain.User;
import com.industrialhub.backend.common.auth.infrastructure.UserRepository;
import com.industrialhub.backend.common.domain.AuditAction;
import com.industrialhub.backend.common.security.JwtService;
import com.industrialhub.backend.common.security.LoginRateLimiter;
import com.industrialhub.backend.common.security.TooManyRequestsException;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LoginUseCaseTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private LoginRateLimiter rateLimiter;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private LoginUseCase loginUseCase;

    private static final String TEST_IP = "127.0.0.1";

    @Test
    void validCredentials_returnsToken() {
        User user = activeUser("admin", "hashed", Role.ADMIN);
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("admin", "hashed")).thenReturn(true);
        when(jwtService.generateToken("admin", "ADMIN", false)).thenReturn("jwt-token");
        when(jwtService.getExpirationMs()).thenReturn(28800000L);

        LoginResponseDto response = loginUseCase.execute(new LoginRequestDto("admin", "admin"), TEST_IP);

        assertThat(response.token()).isEqualTo("jwt-token");
        assertThat(response.username()).isEqualTo("admin");
        assertThat(response.role()).isEqualTo("ADMIN");
        assertThat(response.expiresInMs()).isEqualTo(28800000L);
        assertThat(response.mustChangePassword()).isFalse();
    }

    @Test
    void validCredentials_doesNotLogAuditEvent() {
        User user = activeUser("admin", "hashed", Role.ADMIN);
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("admin", "hashed")).thenReturn(true);
        when(jwtService.generateToken("admin", "ADMIN", false)).thenReturn("jwt-token");
        when(jwtService.getExpirationMs()).thenReturn(28800000L);

        loginUseCase.execute(new LoginRequestDto("admin", "admin"), TEST_IP);

        verify(auditService, never()).logWithIp(any(), any(), any(), any(), any(), any());
    }

    @Test
    void unknownUsername_throwsInvalidCredentials() {
        when(userRepository.findByUsername("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> loginUseCase.execute(new LoginRequestDto("unknown", "pass"), TEST_IP))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Credenciais inválidas");
    }

    @Test
    void unknownUsername_logsLoginFailedAudit() {
        when(userRepository.findByUsername("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> loginUseCase.execute(new LoginRequestDto("unknown", "pass"), TEST_IP))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(auditService).logWithIp(
                eq("unknown"),
                eq(AuditAction.LOGIN_FAILED),
                eq("Auth"),
                eq("unknown"),
                isNull(),
                eq(TEST_IP)
        );
    }

    @Test
    void wrongPassword_throwsInvalidCredentials() {
        User user = activeUser("operator", "hashed", Role.OPERATOR);
        when(userRepository.findByUsername("operator")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

        assertThatThrownBy(() -> loginUseCase.execute(new LoginRequestDto("operator", "wrong"), TEST_IP))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void wrongPassword_logsLoginFailedAudit() {
        User user = activeUser("operator", "hashed", Role.OPERATOR);
        when(userRepository.findByUsername("operator")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

        assertThatThrownBy(() -> loginUseCase.execute(new LoginRequestDto("operator", "wrong"), TEST_IP))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(auditService).logWithIp(
                eq("operator"),
                eq(AuditAction.LOGIN_FAILED),
                eq("Auth"),
                eq("operator"),
                isNull(),
                eq(TEST_IP)
        );
    }

    @Test
    void inactiveUser_throwsInvalidCredentials() {
        User user = User.builder()
                .id(UUID.randomUUID())
                .username("inactive")
                .password("hashed")
                .role(Role.OPERATOR)
                .active(false)
                .build();
        when(userRepository.findByUsername("inactive")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> loginUseCase.execute(new LoginRequestDto("inactive", "pass"), TEST_IP))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(passwordEncoder, never()).matches(any(), any());
    }

    @Test
    void supervisorRole_isCorrectlyReturned() {
        User user = activeUser("supervisor", "hashed", Role.SUPERVISOR);
        when(userRepository.findByUsername("supervisor")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("supervisor", "hashed")).thenReturn(true);
        when(jwtService.generateToken("supervisor", "SUPERVISOR", false)).thenReturn("sup-token");
        when(jwtService.getExpirationMs()).thenReturn(28800000L);

        LoginResponseDto response = loginUseCase.execute(new LoginRequestDto("supervisor", "supervisor"), TEST_IP);

        assertThat(response.role()).isEqualTo("SUPERVISOR");
        verify(jwtService).generateToken("supervisor", "SUPERVISOR", false);
    }

    @Test
    void mustChangePassword_isIncludedInResponse() {
        User user = User.builder()
                .id(UUID.randomUUID())
                .username("newuser")
                .password("hashed")
                .role(Role.OPERATOR)
                .active(true)
                .mustChangePassword(true)
                .build();
        when(userRepository.findByUsername("newuser")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("pass", "hashed")).thenReturn(true);
        when(jwtService.generateToken("newuser", "OPERATOR", true)).thenReturn("token-mcp");
        when(jwtService.getExpirationMs()).thenReturn(28800000L);

        LoginResponseDto response = loginUseCase.execute(new LoginRequestDto("newuser", "pass"), TEST_IP);

        assertThat(response.mustChangePassword()).isTrue();
        verify(jwtService).generateToken("newuser", "OPERATOR", true);
    }

    @Test
    void rateLimiter_throwsTooManyRequests_blockLoginExecution() {
        doThrow(new TooManyRequestsException("Muitas tentativas. Tente novamente em 5 minutos.", 300L))
                .when(rateLimiter).checkLimit(TEST_IP, "admin");

        assertThatThrownBy(() -> loginUseCase.execute(new LoginRequestDto("admin", "admin"), TEST_IP))
                .isInstanceOf(TooManyRequestsException.class)
                .hasMessageContaining("Muitas tentativas")
                .satisfies(ex -> assertThat(((TooManyRequestsException) ex).getRetryAfterSeconds()).isEqualTo(300L));

        // Nenhuma validação de credenciais deve ocorrer após rate limit
        verify(userRepository, never()).findByUsername(any());
        verify(passwordEncoder, never()).matches(any(), any());
    }

    @Test
    void rateLimiter_isCalledBeforeCredentialValidation() {
        doThrow(new TooManyRequestsException("Bloqueado", 300L))
                .when(rateLimiter).checkLimit(anyString(), anyString());

        assertThatThrownBy(() -> loginUseCase.execute(new LoginRequestDto("any", "any"), "1.2.3.4"))
                .isInstanceOf(TooManyRequestsException.class);

        verify(rateLimiter).checkLimit("1.2.3.4", "any");
        verify(userRepository, never()).findByUsername(any());
    }

    private User activeUser(String username, String password, Role role) {
        return User.builder()
                .id(UUID.randomUUID())
                .username(username)
                .password(password)
                .role(role)
                .active(true)
                .build();
    }
}
