package com.industrialhub.backend.common.auth.application.usecase;

import com.industrialhub.backend.common.application.AuditService;
import com.industrialhub.backend.common.auth.domain.Role;
import com.industrialhub.backend.common.auth.domain.User;
import com.industrialhub.backend.common.auth.domain.UserNotFoundException;
import com.industrialhub.backend.common.auth.infrastructure.UserRepository;
import com.industrialhub.backend.common.domain.AuditAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReactivateUserUseCaseTest {

    @Mock private UserRepository userRepository;
    @Mock private AuditService auditService;
    private ReactivateUserUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ReactivateUserUseCase(userRepository, auditService);
    }

    @Test
    void userNotFound_throwsUserNotFoundException() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(id, "admin"))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void inactiveUser_isReactivated() {
        UUID id = UUID.randomUUID();
        User user = User.builder()
                .id(id).username("joao").role(Role.OPERATOR).active(false).build();
        when(userRepository.findById(id)).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        useCase.execute(id, "admin");

        assertThat(user.isActive()).isTrue();
        verify(userRepository).save(user);
        verify(auditService).log(eq("admin"), eq(AuditAction.USER_REACTIVATED), eq("User"), eq(id), any());
    }
}
