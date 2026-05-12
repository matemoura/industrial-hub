package com.industrialhub.backend.common.auth.application.usecase;

import com.industrialhub.backend.common.auth.application.dto.LoginRequestDto;
import com.industrialhub.backend.common.auth.application.dto.LoginResponseDto;
import com.industrialhub.backend.common.auth.domain.Role;
import com.industrialhub.backend.common.auth.domain.User;
import com.industrialhub.backend.common.auth.infrastructure.UserRepository;
import com.industrialhub.backend.common.security.JwtService;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LoginUseCaseTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private LoginUseCase loginUseCase;

    @Test
    void validCredentials_returnsToken() {
        User user = activeUser("admin", "hashed", Role.ADMIN);
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("admin", "hashed")).thenReturn(true);
        when(jwtService.generateToken("admin", "ADMIN")).thenReturn("jwt-token");
        when(jwtService.getExpirationMs()).thenReturn(28800000L);

        LoginResponseDto response = loginUseCase.execute(new LoginRequestDto("admin", "admin"));

        assertThat(response.token()).isEqualTo("jwt-token");
        assertThat(response.username()).isEqualTo("admin");
        assertThat(response.role()).isEqualTo("ADMIN");
        assertThat(response.expiresInMs()).isEqualTo(28800000L);
    }

    @Test
    void unknownUsername_throwsInvalidCredentials() {
        when(userRepository.findByUsername("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> loginUseCase.execute(new LoginRequestDto("unknown", "pass")))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Credenciais inválidas");
    }

    @Test
    void wrongPassword_throwsInvalidCredentials() {
        User user = activeUser("operator", "hashed", Role.OPERATOR);
        when(userRepository.findByUsername("operator")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

        assertThatThrownBy(() -> loginUseCase.execute(new LoginRequestDto("operator", "wrong")))
                .isInstanceOf(InvalidCredentialsException.class);
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

        assertThatThrownBy(() -> loginUseCase.execute(new LoginRequestDto("inactive", "pass")))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(passwordEncoder, never()).matches(any(), any());
    }

    @Test
    void supervisorRole_isCorrectlyReturned() {
        User user = activeUser("supervisor", "hashed", Role.SUPERVISOR);
        when(userRepository.findByUsername("supervisor")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("supervisor", "hashed")).thenReturn(true);
        when(jwtService.generateToken("supervisor", "SUPERVISOR")).thenReturn("sup-token");
        when(jwtService.getExpirationMs()).thenReturn(28800000L);

        LoginResponseDto response = loginUseCase.execute(new LoginRequestDto("supervisor", "supervisor"));

        assertThat(response.role()).isEqualTo("SUPERVISOR");
        verify(jwtService).generateToken("supervisor", "SUPERVISOR");
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
