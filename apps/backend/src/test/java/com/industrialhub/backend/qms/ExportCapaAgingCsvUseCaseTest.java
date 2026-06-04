package com.industrialhub.backend.qms;

import com.industrialhub.backend.qms.application.usecase.ExportCapaAgingCsvUseCase;
import com.industrialhub.backend.qms.infrastructure.CorrectiveActionRepository;
import com.industrialhub.backend.qms.infrastructure.projection.CapaAgingProjection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExportCapaAgingCsvUseCaseTest {

    @Mock
    private CorrectiveActionRepository correctiveActionRepository;

    private ExportCapaAgingCsvUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ExportCapaAgingCsvUseCase(correctiveActionRepository);
    }

    @Test
    void execute_emptyList_returnsHeaderOnlyWithBom() {
        when(correctiveActionRepository.findOpenCapasForAging()).thenReturn(List.of());

        byte[] result = useCase.execute();

        // UTF-8 BOM is 0xEF, 0xBB, 0xBF
        assertThat(result[0] & 0xFF).isEqualTo(0xEF);
        assertThat(result[1] & 0xFF).isEqualTo(0xBB);
        assertThat(result[2] & 0xFF).isEqualTo(0xBF);

        String content = new String(result, 3, result.length - 3, java.nio.charset.StandardCharsets.UTF_8);
        assertThat(content).contains("ncSeverity;status;dueDate;diasParaVencer;situacao");
    }

    @Test
    void execute_withCapas_containsDataRows() {
        LocalDate dueDate = LocalDate.now().plusDays(5);
        List<CapaAgingProjection> projections = List.of(
                mockProjection(dueDate, "HIGH", "PENDING")
        );

        when(correctiveActionRepository.findOpenCapasForAging()).thenReturn(projections);

        byte[] result = useCase.execute();
        String content = new String(result, java.nio.charset.StandardCharsets.UTF_8);

        assertThat(content).contains("HIGH");
        assertThat(content).contains("PENDING");
        assertThat(content).contains("Em dia");
    }

    @Test
    void execute_overdueCapas_showsVencida() {
        LocalDate pastDate = LocalDate.now().minusDays(3);
        List<CapaAgingProjection> projections = List.of(
                mockProjection(pastDate, "CRITICAL", "PENDING")
        );

        when(correctiveActionRepository.findOpenCapasForAging()).thenReturn(projections);

        byte[] result = useCase.execute();
        String content = new String(result, java.nio.charset.StandardCharsets.UTF_8);

        assertThat(content).contains("Vencida");
    }

    @Test
    void execute_noDueDate_showsSemPrazo() {
        List<CapaAgingProjection> projections = List.of(
                mockProjection(null, "LOW", "PENDING_EFFECTIVENESS")
        );

        when(correctiveActionRepository.findOpenCapasForAging()).thenReturn(projections);

        byte[] result = useCase.execute();
        String content = new String(result, java.nio.charset.StandardCharsets.UTF_8);

        assertThat(content).contains("Sem prazo");
    }

    private CapaAgingProjection mockProjection(LocalDate dueDate, String severity, String status) {
        return new CapaAgingProjection() {
            public UUID getActionId() { return UUID.randomUUID(); }
            public LocalDate getDueDate() { return dueDate; }
            public String getStatus() { return status; }
            public String getNcSeverity() { return severity; }
        };
    }
}
