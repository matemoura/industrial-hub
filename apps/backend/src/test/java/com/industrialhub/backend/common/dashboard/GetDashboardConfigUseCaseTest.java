package com.industrialhub.backend.common.dashboard;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.industrialhub.backend.common.application.AuditService;
import com.industrialhub.backend.common.application.DashboardDefaultLayouts;
import com.industrialhub.backend.common.application.dto.SaveDashboardConfigRequest;
import com.industrialhub.backend.common.application.dto.UserDashboardConfigResponse;
import com.industrialhub.backend.common.application.usecase.DeleteDashboardConfigUseCase;
import com.industrialhub.backend.common.application.usecase.GetDashboardConfigUseCase;
import com.industrialhub.backend.common.application.usecase.SaveDashboardConfigUseCase;
import com.industrialhub.backend.common.domain.UserDashboardConfig;
import com.industrialhub.backend.common.infrastructure.UserDashboardConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetDashboardConfigUseCaseTest {

    @Mock
    private UserDashboardConfigRepository repository;

    @Mock
    private AuditService auditService;

    private GetDashboardConfigUseCase useCase;
    private SaveDashboardConfigUseCase saveUseCase;
    private DeleteDashboardConfigUseCase deleteUseCase;

    @BeforeEach
    void setUp() {
        useCase = new GetDashboardConfigUseCase(repository);
        saveUseCase = new SaveDashboardConfigUseCase(repository, auditService, new ObjectMapper());
        deleteUseCase = new DeleteDashboardConfigUseCase(repository, auditService);
    }

    @Test
    void shouldReturnSavedConfig_whenUserHasConfig() {
        String customJson = "[{\"id\":\"w1\",\"type\":\"oee-avg\",\"column\":1,\"row\":1}]";
        UserDashboardConfig saved = UserDashboardConfig.builder()
                .id(UUID.randomUUID())
                .username("operator1")
                .widgetsJson(customJson)
                .updatedAt(LocalDateTime.now())
                .build();

        when(repository.findByUsername("operator1")).thenReturn(Optional.of(saved));

        UserDashboardConfigResponse result = useCase.execute("operator1", "OPERATOR");

        assertThat(result.widgetsJson()).isEqualTo(customJson);
    }

    @Test
    void shouldReturnDefaultWith6Widgets_whenOperatorHasNoConfig() {
        when(repository.findByUsername("op1")).thenReturn(Optional.empty());

        UserDashboardConfigResponse result = useCase.execute("op1", "OPERATOR");

        assertThat(result.widgetsJson()).isEqualTo(DashboardDefaultLayouts.forRole("OPERATOR"));
        assertThat(result.widgetsJson()).contains("oee-avg");
        assertThat(result.widgetsJson()).doesNotContain("oee-trend");
        assertThat(result.widgetsJson()).doesNotContain("nc-pareto");
    }

    @Test
    void shouldReturnDefaultWith8Widgets_whenSupervisorHasNoConfig() {
        when(repository.findByUsername("sup1")).thenReturn(Optional.empty());

        UserDashboardConfigResponse result = useCase.execute("sup1", "SUPERVISOR");

        assertThat(result.widgetsJson()).isEqualTo(DashboardDefaultLayouts.forRole("SUPERVISOR"));
        assertThat(result.widgetsJson()).contains("oee-trend");
        assertThat(result.widgetsJson()).contains("nc-pareto");
    }

    @Test
    void shouldReturnDefaultWith8Widgets_whenAdminHasNoConfig() {
        when(repository.findByUsername("admin1")).thenReturn(Optional.empty());

        UserDashboardConfigResponse result = useCase.execute("admin1", "ADMIN");

        assertThat(result.widgetsJson()).contains("oee-trend");
        assertThat(result.widgetsJson()).contains("nc-pareto");
    }

    @Test
    void afterSaveAndDelete_shouldReturnDefault() {
        String username = "operator_flow";
        String customJson = "[{\"id\":\"w1\",\"type\":\"oee-avg\",\"column\":1,\"row\":1}]";

        // Step 1: save — repository.findByUsername returns empty (new config), then save returns entity
        UserDashboardConfig savedEntity = UserDashboardConfig.builder()
                .id(UUID.randomUUID())
                .username(username)
                .widgetsJson(customJson)
                .updatedAt(LocalDateTime.now())
                .build();
        when(repository.findByUsername(username))
                .thenReturn(Optional.empty())   // first call: during save (no existing config)
                .thenReturn(Optional.empty());  // third call: after delete, get returns default
        when(repository.save(any(UserDashboardConfig.class))).thenReturn(savedEntity);

        // Step 2: delete — stubbing deleteByUsername (void method, default is no-op with Mockito)

        // Execute PUT → DELETE → GET flow
        saveUseCase.execute(username, new SaveDashboardConfigRequest(customJson));
        deleteUseCase.execute(username);
        UserDashboardConfigResponse result = useCase.execute(username, "OPERATOR");

        // After delete, repository returns empty → use case falls back to OPERATOR default (6 widgets)
        assertThat(result.widgetsJson()).isEqualTo(DashboardDefaultLayouts.forRole("OPERATOR"));
        assertThat(result.widgetsJson()).contains("oee-avg");
        assertThat(result.widgetsJson()).contains("nc-open");
        assertThat(result.widgetsJson()).doesNotContain("oee-trend");
        assertThat(result.widgetsJson()).doesNotContain("nc-pareto");
    }
}
