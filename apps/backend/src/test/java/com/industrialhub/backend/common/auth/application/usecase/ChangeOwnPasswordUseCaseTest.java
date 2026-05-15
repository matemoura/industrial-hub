package com.industrialhub.backend.common.auth.application.usecase;

import com.industrialhub.backend.common.application.AuditService;
import com.industrialhub.backend.common.auth.application.dto.ChangePasswordRequest;
import com.industrialhub.backend.common.auth.application.dto.LoginResponseDto;
import com.industrialhub.backend.common.auth.domain.InvalidPasswordException;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChangeOwnPasswordUseCaseTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;
    @Mock private AuditService auditService;

    @InjectMocks private ChangeOwnPasswordUseCase useCase;

    @Test
    void wrongCurrentPassword_throwsInvalidPassword() {
        User user = activeUser("joao", "hashed-old");
        when(userRepository.findByUsername("joao")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "hashed-old")).thenReturn(false);

        assertThatThrownBy(() -> useCase.execute("joao",
                new ChangePasswordRequest("wrong", "NovaSenha1")))
                .isInstanceOf(InvalidPasswordException.class)
                .hasMessageContaining("Senha atual incorreta");
    }

    @Test
    void samePasswordAsOld_throwsInvalidPassword() {
        User user = activeUser("joao", "hashed-old");
        when(userRepository.findByUsername("joao")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Atual1234", "hashed-old")).thenReturn(true);
        // Simula que nova senha também bate com o hash atual
        when(passwordEncoder.matches("Atual1234", "hashed-old")).thenReturn(true);

        assertThatThrownBy(() -> useCase.execute("joao",
                new ChangePasswordRequest("Atual1234", "Atual1234")))
                .isInstanceOf(InvalidPasswordException.class)
                .hasMessageContaining("diferente da senha atual");
    }

    @Test
    void weakNewPassword_throwsInvalidPassword() {
        User user = activeUser("joao", "hashed-old");
        when(userRepository.findByUsername("joao")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Atual1234", "hashed-old")).thenReturn(true);
        when(passwordEncoder.matches("fraca", "hashed-old")).thenReturn(false);

        assertThatThrownBy(() -> useCase.execute("joao",
                new ChangePasswordRequest("Atual1234", "fraca")))
                .isInstanceOf(InvalidPasswordException.class);
    }

    @Test
    void success_setsMustChangePasswordFalseAndReturnsNewToken() {
        User user = User.builder()
                .id(UUID.randomUUID()).username("joao").password("hashed-old")
                .role(Role.SUPERVISOR).active(true).mustChangePassword(true).build();
        when(userRepository.findByUsername("joao")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Atual1234", "hashed-old")).thenReturn(true);
        when(passwordEncoder.matches("NovaSenha1", "hashed-old")).thenReturn(false);
        when(passwordEncoder.encode("NovaSenha1")).thenReturn("hashed-new");
        when(jwtService.generateToken("joao", "SUPERVISOR", false)).thenReturn("new-jwt");
        when(jwtService.getExpirationMs()).thenReturn(28800000L);

        LoginResponseDto response = useCase.execute("joao",
                new ChangePasswordRequest("Atual1234", "NovaSenha1"));

        assertThat(user.isMustChangePassword()).isFalse();
        assertThat(user.getPassword()).isEqualTo("hashed-new");
        assertThat(response.token()).isEqualTo("new-jwt");
        assertThat(response.mustChangePassword()).isFalse();
        verify(userRepository).save(user);
    }

    @Test
    void success_logsAuditEvent() {
        User user = activeUser("joao", "hashed-old");
        when(userRepository.findByUsername("joao")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Atual1234", "hashed-old")).thenReturn(true);
        when(passwordEncoder.matches("NovaSenha1", "hashed-old")).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("hashed-new");
        when(jwtService.generateToken(any(), any(), anyBoolean())).thenReturn("new-jwt");
        when(jwtService.getExpirationMs()).thenReturn(28800000L);

        useCase.execute("joao", new ChangePasswordRequest("Atual1234", "NovaSenha1"));

        verify(auditService).log(eq("joao"), any(), eq("User"), eq("joao"), isNull());
    }

    private User activeUser(String username, String hashedPassword) {
        return User.builder()
                .id(UUID.randomUUID()).username(username).password(hashedPassword)
                .role(Role.OPERATOR).active(true).mustChangePassword(false).build();
    }
}
