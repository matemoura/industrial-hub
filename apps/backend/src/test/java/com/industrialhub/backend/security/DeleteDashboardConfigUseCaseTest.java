package com.industrialhub.backend.security;

import com.industrialhub.backend.common.application.AuditService;
import com.industrialhub.backend.common.application.usecase.DeleteDashboardConfigUseCase;
import com.industrialhub.backend.common.domain.AuditAction;
import com.industrialhub.backend.common.domain.UserDashboardConfig;
import com.industrialhub.backend.common.infrastructure.UserDashboardConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * SEC-104: audit log on delete; idempotent — no log when config did not exist.
 */
@ExtendWith(MockitoExtension.class)
class DeleteDashboardConfigUseCaseTest {

    @Mock private UserDashboardConfigRepository repository;
    @Mock private AuditService auditService;

    private DeleteDashboardConfigUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new DeleteDashboardConfigUseCase(repository, auditService);
    }

    @Test
    void shouldDeleteAndAudit_whenConfigExists() {
        // Arrange — SEC-104
        UserDashboardConfig config = UserDashboardConfig.builder()
                .id(UUID.randomUUID()).username("user1").widgetsJson("{}")
                .updatedAt(LocalDateTime.now()).build();
        when(repository.findByUsername("user1")).thenReturn(Optional.of(config));
        doNothing().when(repository).deleteByUsername("user1");

        // Act
        useCase.execute("user1");

        // Assert
        verify(repository).deleteByUsername("user1");
        verify(auditService).log(eq("user1"), eq(AuditAction.DASHBOARD_CONFIG_RESET),
                eq("UserDashboardConfig"), eq((String) "user1"), any());
    }

    @Test
    void shouldDelete_withoutAudit_whenConfigDidNotExist() {
        // Arrange — SEC-104: idempotent; no audit when nothing was deleted
        when(repository.findByUsername("ghost")).thenReturn(Optional.empty());
        doNothing().when(repository).deleteByUsername("ghost");

        // Act
        useCase.execute("ghost");

        // Assert
        verify(repository).deleteByUsername("ghost");
        verify(auditService, never()).log(anyString(), any(), anyString(), anyString(), any());
    }
}
