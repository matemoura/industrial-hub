package com.industrialhub.backend.common.auth.application.usecase;

import com.industrialhub.backend.common.application.AuditService;
import com.industrialhub.backend.common.auth.domain.LastAdminException;
import com.industrialhub.backend.common.auth.domain.Role;
import com.industrialhub.backend.common.auth.domain.User;
import com.industrialhub.backend.common.auth.domain.UserNotFoundException;
import com.industrialhub.backend.common.auth.infrastructure.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeactivateUserUseCaseTest {

    @Mock private UserRepository userRepository;
    @Mock private AuditService auditService;

    @InjectMocks private DeactivateUserUseCase useCase;

    @Test
    void userNotFound_throwsUserNotFoundException() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(id, "admin"))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void lastActiveAdmin_throwsLastAdminException() {
        UUID id = UUID.randomUUID();
        User admin = adminUser(id);
        when(userRepository.findById(id)).thenReturn(Optional.of(admin));
        when(userRepository.countByRoleAndActiveTrue(Role.ADMIN)).thenReturn(1L);

        assertThatThrownBy(() -> useCase.execute(id, "admin"))
                .isInstanceOf(LastAdminException.class)
                .hasMessageContaining("único administrador ativo");
    }

    @Test
    void nonLastAdmin_deactivatesSuccessfully() {
        UUID id = UUID.randomUUID();
        User admin = adminUser(id);
        when(userRepository.findById(id)).thenReturn(Optional.of(admin));
        when(userRepository.countByRoleAndActiveTrue(Role.ADMIN)).thenReturn(2L);

        useCase.execute(id, "admin");

        assertThat(admin.isActive()).isFalse();
        verify(userRepository).save(admin);
    }

    @Test
    void operatorUser_deactivatesWithoutLastAdminCheck() {
        UUID id = UUID.randomUUID();
        User operator = User.builder()
                .id(id).username("op").role(Role.OPERATOR).active(true).build();
        when(userRepository.findById(id)).thenReturn(Optional.of(operator));

        useCase.execute(id, "admin");

        assertThat(operator.isActive()).isFalse();
        verify(userRepository, never()).countByRoleAndActiveTrue(any());
    }

    @Test
    void deactivation_logsAuditEvent() {
        UUID id = UUID.randomUUID();
        User operator = User.builder()
                .id(id).username("op").role(Role.OPERATOR).active(true).build();
        when(userRepository.findById(id)).thenReturn(Optional.of(operator));

        useCase.execute(id, "admin");

        verify(auditService).log(eq("admin"), any(), eq("User"), eq(id), any());
    }

    private User adminUser(UUID id) {
        return User.builder()
                .id(id).username("admin2").role(Role.ADMIN).active(true).build();
    }
}
