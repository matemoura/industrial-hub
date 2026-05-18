package com.industrialhub.backend.common.auth.application.usecase;

import com.industrialhub.backend.common.application.AuditService;
import com.industrialhub.backend.common.auth.application.dto.UpdateUserRoleRequest;
import com.industrialhub.backend.common.auth.application.dto.UserResponse;
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
class UpdateUserRoleUseCaseTest {

    @Mock private UserRepository userRepository;
    @Mock private AuditService auditService;
    private UpdateUserRoleUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new UpdateUserRoleUseCase(userRepository, auditService);
    }

    @Test
    void userNotFound_throwsUserNotFoundException() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(id, new UpdateUserRoleRequest(Role.ADMIN), "admin"))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void validRequest_updatesRoleAndReturnsResponse() {
        UUID id = UUID.randomUUID();
        User user = User.builder()
                .id(id).username("joao").role(Role.OPERATOR).active(true).build();
        when(userRepository.findById(id)).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UserResponse response = useCase.execute(id, new UpdateUserRoleRequest(Role.SUPERVISOR), "admin");

        assertThat(response.role()).isEqualTo(Role.SUPERVISOR);
        assertThat(user.getRole()).isEqualTo(Role.SUPERVISOR);
        verify(userRepository).save(user);
        verify(auditService).log(eq("admin"), eq(AuditAction.ROLE_CHANGED), eq("User"), eq(id), any());
    }
}
