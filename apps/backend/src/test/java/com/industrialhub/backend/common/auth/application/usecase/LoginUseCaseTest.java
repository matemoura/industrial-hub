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
        User user = activeUser("admin", "admin@msb.internal", "hashed", Role.ADMIN);
        when(userRepository.findByEmail("admin@msb.internal")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("admin", "hashed")).thenReturn(true);
        when(jwtService.generateToken("admin", "ADMIN", false)).thenReturn("jwt-token");
        when(jwtService.getExpirationMs()).thenReturn(28800000L);

        LoginResponseDto response = loginUseCase.execute(new LoginRequestDto("admin@msb.internal", "admin"), TEST_IP);

        assertThat(response.token()).isEqualTo("jwt-token");
        assertThat(response.username()).isEqualTo("admin");
        assertThat(response.role()).isEqualTo("ADMIN");
        assertThat(response.email()).isEqualTo("admin@msb.internal");
        assertThat(response.expiresInMs()).isEqualTo(28800000L);
        assertThat(response.mustChangePassword()).isFalse();
    }

    @Test
    void validCredentials_doesNotLogAuditEvent() {
        User user = activeUser("admin", "admin@msb.internal", "hashed", Role.ADMIN);
        when(userRepository.findByEmail("admin@msb.internal")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("admin", "hashed")).thenReturn(true);
        when(jwtService.generateToken("admin", "ADMIN", false)).thenReturn("jwt-token");
        when(jwtService.getExpirationMs()).thenReturn(28800000L);

        loginUseCase.execute(new LoginRequestDto("admin@msb.internal", "admin"), TEST_IP);

        verify(auditService, never()).logWithIp(any(), any(), any(), any(), any(), any());
    }

    @Test
    void unknownEmail_throwsInvalidCredentials() {
        when(userRepository.findByEmail("unknown@test.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> loginUseCase.execute(new LoginRequestDto("unknown@test.com", "pass"), TEST_IP))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Credenciais inválidas");
    }

    @Test
    void unknownEmail_logsLoginFailedAudit() {
        when(userRepository.findByEmail("unknown@test.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> loginUseCase.execute(new LoginRequestDto("unknown@test.com", "pass"), TEST_IP))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(auditService).logWithIp(
                eq("unknown@test.com"),
                eq(AuditAction.LOGIN_FAILED),
                eq("Auth"),
                eq("unknown@test.com"),
                isNull(),
                eq(TEST_IP)
        );
    }

    @Test
    void wrongPassword_throwsInvalidCredentials() {
        User user = activeUser("operator", "operator@msb.internal", "hashed", Role.OPERATOR);
        when(userRepository.findByEmail("operator@msb.internal")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

        assertThatThrownBy(() -> loginUseCase.execute(new LoginRequestDto("operator@msb.internal", "wrong"), TEST_IP))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void wrongPassword_logsLoginFailedAudit() {
        User user = activeUser("operator", "operator@msb.internal", "hashed", Role.OPERATOR);
        when(userRepository.findByEmail("operator@msb.internal")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

        assertThatThrownBy(() -> loginUseCase.execute(new LoginRequestDto("operator@msb.internal", "wrong"), TEST_IP))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(auditService).logWithIp(
                eq("operator@msb.internal"),
                eq(AuditAction.LOGIN_FAILED),
                eq("Auth"),
                eq("operator@msb.internal"),
                isNull(),
                eq(TEST_IP)
        );
    }

    @Test
    void inactiveUser_throwsInvalidCredentials() {
        User user = User.builder()
                .id(UUID.randomUUID())
                .username("inactive")
                .email("inactive@msb.internal")
                .password("hashed")
                .role(Role.OPERATOR)
                .active(false)
                .build();
        when(userRepository.findByEmail("inactive@msb.internal")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> loginUseCase.execute(new LoginRequestDto("inactive@msb.internal", "pass"), TEST_IP))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(passwordEncoder, never()).matches(any(), any());
    }

    @Test
    void supervisorRole_isCorrectlyReturned() {
        User user = activeUser("supervisor", "supervisor@msb.internal", "hashed", Role.SUPERVISOR);
        when(userRepository.findByEmail("supervisor@msb.internal")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("supervisor", "hashed")).thenReturn(true);
        when(jwtService.generateToken("supervisor", "SUPERVISOR", false)).thenReturn("sup-token");
        when(jwtService.getExpirationMs()).thenReturn(28800000L);

        LoginResponseDto response = loginUseCase.execute(new LoginRequestDto("supervisor@msb.internal", "supervisor"), TEST_IP);

        assertThat(response.role()).isEqualTo("SUPERVISOR");
        verify(jwtService).generateToken("supervisor", "SUPERVISOR", false);
    }

    @Test
    void mustChangePassword_isIncludedInResponse() {
        User user = User.builder()
                .id(UUID.randomUUID())
                .username("newuser")
                .email("newuser@msb.internal")
                .password("hashed")
                .role(Role.OPERATOR)
                .active(true)
                .mustChangePassword(true)
                .build();
        when(userRepository.findByEmail("newuser@msb.internal")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("pass", "hashed")).thenReturn(true);
        when(jwtService.generateToken("newuser", "OPERATOR", true)).thenReturn("token-mcp");
        when(jwtService.getExpirationMs()).thenReturn(28800000L);

        LoginResponseDto response = loginUseCase.execute(new LoginRequestDto("newuser@msb.internal", "pass"), TEST_IP);

        assertThat(response.mustChangePassword()).isTrue();
        verify(jwtService).generateToken("newuser", "OPERATOR", true);
    }

    @Test
    void rateLimiter_throwsTooManyRequests_blockLoginExecution() {
        doThrow(new TooManyRequestsException("Muitas tentativas. Tente novamente em 5 minutos.", 300L))
                .when(rateLimiter).checkLimit(TEST_IP, "admin@msb.internal");

        assertThatThrownBy(() -> loginUseCase.execute(new LoginRequestDto("admin@msb.internal", "admin"), TEST_IP))
                .isInstanceOf(TooManyRequestsException.class)
                .hasMessageContaining("Muitas tentativas")
                .satisfies(ex -> assertThat(((TooManyRequestsException) ex).getRetryAfterSeconds()).isEqualTo(300L));

        verify(userRepository, never()).findByEmail(any());
        verify(passwordEncoder, never()).matches(any(), any());
    }

    @Test
    void rateLimiter_isCalledBeforeCredentialValidation() {
        doThrow(new TooManyRequestsException("Bloqueado", 300L))
                .when(rateLimiter).checkLimit(anyString(), anyString());

        assertThatThrownBy(() -> loginUseCase.execute(new LoginRequestDto("any@test.com", "any"), "1.2.3.4"))
                .isInstanceOf(TooManyRequestsException.class);

        verify(rateLimiter).checkLimit("1.2.3.4", "any@test.com");
        verify(userRepository, never()).findByEmail(any());
    }

    private User activeUser(String username, String email, String password, Role role) {
        return User.builder()
                .id(UUID.randomUUID())
                .username(username)
                .email(email)
                .password(password)
                .role(role)
                .active(true)
                .build();
    }
}
