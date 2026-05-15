package com.industrialhub.backend.common.auth.application.usecase;

import com.industrialhub.backend.common.application.AuditService;
import com.industrialhub.backend.common.auth.application.dto.CreateUserRequest;
import com.industrialhub.backend.common.auth.application.dto.UserResponse;
import com.industrialhub.backend.common.auth.domain.InvalidPasswordException;
import com.industrialhub.backend.common.auth.domain.Role;
import com.industrialhub.backend.common.auth.domain.User;
import com.industrialhub.backend.common.auth.domain.UserAlreadyExistsException;
import com.industrialhub.backend.common.auth.infrastructure.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CreateUserUseCaseTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AuditService auditService;

    @InjectMocks private CreateUserUseCase useCase;

    @Test
    void duplicateUsername_throwsUserAlreadyExists() {
        when(userRepository.existsByUsername("joao")).thenReturn(true);

        assertThatThrownBy(() -> useCase.execute(
                new CreateUserRequest("joao", "joao@msb.com", Role.OPERATOR, "Senha1234"),
                "admin"))
                .isInstanceOf(UserAlreadyExistsException.class)
                .hasMessageContaining("joao");
    }

    @Test
    void validRequest_savesUserWithMustChangePasswordTrue() {
        when(userRepository.existsByUsername("maria")).thenReturn(false);
        when(passwordEncoder.encode("Senha1234")).thenReturn("hashed");

        User saved = savedUser("maria", Role.SUPERVISOR);
        when(userRepository.save(any())).thenReturn(saved);

        UserResponse response = useCase.execute(
                new CreateUserRequest("maria", "maria@msb.com", Role.SUPERVISOR, "Senha1234"),
                "admin");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());

        assertThat(captor.getValue().isMustChangePassword()).isTrue();
        assertThat(captor.getValue().isActive()).isTrue();
        assertThat(captor.getValue().getPassword()).isEqualTo("hashed");
        assertThat(response.mustChangePassword()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc1A", "abcdefgh", "ABCDEFGH"})
    void weakPassword_throwsInvalidPassword(String weak) {
        when(userRepository.existsByUsername(any())).thenReturn(false);

        assertThatThrownBy(() -> useCase.execute(
                new CreateUserRequest("user", "u@msb.com", Role.OPERATOR, weak),
                "admin"))
                .isInstanceOf(InvalidPasswordException.class);
    }

    @Test
    void tooShortPassword_throwsInvalidPassword() {
        when(userRepository.existsByUsername(any())).thenReturn(false);

        assertThatThrownBy(() -> useCase.execute(
                new CreateUserRequest("user", "u@msb.com", Role.OPERATOR, "Ab1"),
                "admin"))
                .isInstanceOf(InvalidPasswordException.class)
                .hasMessageContaining("8 caracteres");
    }

    @Test
    void passwordWithoutUppercase_throwsInvalidPassword() {
        when(userRepository.existsByUsername(any())).thenReturn(false);

        assertThatThrownBy(() -> useCase.execute(
                new CreateUserRequest("user", "u@msb.com", Role.OPERATOR, "senha1234"),
                "admin"))
                .isInstanceOf(InvalidPasswordException.class);
    }

    @Test
    void passwordWithoutDigit_throwsInvalidPassword() {
        when(userRepository.existsByUsername(any())).thenReturn(false);

        assertThatThrownBy(() -> useCase.execute(
                new CreateUserRequest("user", "u@msb.com", Role.OPERATOR, "SenhaSemNum"),
                "admin"))
                .isInstanceOf(InvalidPasswordException.class);
    }

    @Test
    void validRequest_logsAuditEvent() {
        when(userRepository.existsByUsername(any())).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("hashed");
        when(userRepository.save(any())).thenReturn(savedUser("user", Role.OPERATOR));

        useCase.execute(new CreateUserRequest("user", "u@msb.com", Role.OPERATOR, "Senha1234"), "admin");

        verify(auditService).log(eq("admin"), any(), eq("User"), any(UUID.class), any());
    }

    private User savedUser(String username, Role role) {
        return User.builder()
                .id(UUID.randomUUID())
                .username(username)
                .email(username + "@msb.com")
                .role(role)
                .active(true)
                .mustChangePassword(true)
                .password("hashed")
                .build();
    }
}
