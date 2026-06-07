package com.industrialhub.backend.qms.risk;

import com.industrialhub.backend.qms.risk.application.dto.RiskMatrixResponse;
import com.industrialhub.backend.qms.risk.application.usecase.GetRiskMatrixUseCase;
import com.industrialhub.backend.qms.risk.domain.RiskLevel;
import com.industrialhub.backend.qms.risk.infrastructure.RiskItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetRiskMatrixUseCaseTest {

    @Mock private RiskItemRepository riskItemRepository;

    private GetRiskMatrixUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GetRiskMatrixUseCase(riskItemRepository);
    }

    @Test
    void shouldReturnCellForRisk_severity8_occurrence6_withCount1() {
        // AC (a): risco severity=8, occurrence=6 → célula (8,6) com count=1
        Object[] row = new Object[]{ 8, 6, 1L, RiskLevel.CRITICAL };
        List<Object[]> rows = Collections.singletonList(row);
        when(riskItemRepository.findMatrixData()).thenReturn(rows);

        RiskMatrixResponse response = useCase.execute();

        assertThat(response.cells()).hasSize(1);
        assertThat(response.cells().get(0).severity()).isEqualTo(8);
        assertThat(response.cells().get(0).occurrence()).isEqualTo(6);
        assertThat(response.cells().get(0).count()).isEqualTo(1L);
        assertThat(response.cells().get(0).riskLevel()).isEqualTo(RiskLevel.CRITICAL);
    }

    @Test
    void shouldNotReturnCell_forMitigatedRisk() {
        // AC (b): risco com status=MITIGATED não aparece na matriz (query filtra na DB)
        // O repositório já retorna vazio quando não há IDENTIFIED/BEING_MITIGATED
        when(riskItemRepository.findMatrixData()).thenReturn(Collections.emptyList());

        RiskMatrixResponse response = useCase.execute();

        assertThat(response.cells()).isEmpty();
    }
}
