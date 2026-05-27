package com.industrialhub.backend.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.industrialhub.backend.common.application.AuditService;
import com.industrialhub.backend.common.application.dto.SaveDashboardConfigRequest;
import com.industrialhub.backend.common.application.dto.UserDashboardConfigResponse;
import com.industrialhub.backend.common.application.usecase.SaveDashboardConfigUseCase;
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

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * SEC-102: widgetsJson validation; SEC-104: audit log on save.
 */
@ExtendWith(MockitoExtension.class)
class SaveDashboardConfigUseCaseTest {

    @Mock private UserDashboardConfigRepository repository;
    @Mock private AuditService auditService;

    private SaveDashboardConfigUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new SaveDashboardConfigUseCase(repository, auditService, new ObjectMapper());
    }

    @Test
    void shouldSaveConfig_whenWidgetsJsonIsValid() {
        // Arrange
        String validJson = "[{\"id\":\"oee\",\"position\":0}]";
        SaveDashboardConfigRequest request = new SaveDashboardConfigRequest(validJson);

        UserDashboardConfig saved = UserDashboardConfig.builder()
                .id(UUID.randomUUID()).username("user1").widgetsJson(validJson)
                .updatedAt(LocalDateTime.now()).build();
        when(repository.findByUsername("user1")).thenReturn(Optional.empty());
        when(repository.save(any())).thenReturn(saved);

        // Act
        UserDashboardConfigResponse result = useCase.execute("user1", request);

        // Assert
        assertThat(result.widgetsJson()).isEqualTo(validJson);
        verify(repository).save(any(UserDashboardConfig.class));
    }

    @Test
    void shouldThrowIllegalArgument_whenWidgetsJsonIsInvalid() {
        // Arrange — SEC-102: invalid JSON must be rejected before persist
        SaveDashboardConfigRequest request = new SaveDashboardConfigRequest("{not-valid-json}");

        // Act / Assert
        assertThatThrownBy(() -> useCase.execute("user1", request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("JSON inválido");

        verify(repository, never()).save(any());
    }

    @Test
    void shouldAuditLog_afterSuccessfulSave() {
        // Arrange — SEC-104
        String validJson = "{}";
        SaveDashboardConfigRequest request = new SaveDashboardConfigRequest(validJson);

        UserDashboardConfig saved = UserDashboardConfig.builder()
                .id(UUID.randomUUID()).username("user2").widgetsJson(validJson)
                .updatedAt(LocalDateTime.now()).build();
        when(repository.findByUsername("user2")).thenReturn(Optional.empty());
        when(repository.save(any())).thenReturn(saved);

        // Act
        useCase.execute("user2", request);

        // Assert
        verify(auditService).log(eq("user2"), eq(AuditAction.DASHBOARD_CONFIG_SAVED),
                eq("UserDashboardConfig"), anyString(), any());
    }

    @Test
    void shouldUpdateExistingConfig_whenUsernameAlreadyHasConfig() {
        // Arrange
        String oldJson = "[{\"id\":\"oee\"}]";
        String newJson = "[{\"id\":\"oee\"},{\"id\":\"kpi\"}]";
        SaveDashboardConfigRequest request = new SaveDashboardConfigRequest(newJson);

        UserDashboardConfig existing = UserDashboardConfig.builder()
                .id(UUID.randomUUID()).username("user3").widgetsJson(oldJson)
                .updatedAt(LocalDateTime.now()).build();
        when(repository.findByUsername("user3")).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Act
        UserDashboardConfigResponse result = useCase.execute("user3", request);

        // Assert
        assertThat(result.widgetsJson()).isEqualTo(newJson);
        assertThat(existing.getWidgetsJson()).isEqualTo(newJson); // updated in place
    }
}
