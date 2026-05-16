package com.industrialhub.backend.qms;

import com.industrialhub.backend.qms.application.dto.RcaResponse;
import com.industrialhub.backend.qms.application.usecase.GetRcaByNcUseCase;
import com.industrialhub.backend.qms.domain.NcStatus;
import com.industrialhub.backend.qms.domain.NonConformance;
import com.industrialhub.backend.qms.domain.RcaNotFoundException;
import com.industrialhub.backend.qms.domain.RootCauseAnalysis;
import com.industrialhub.backend.qms.infrastructure.RootCauseAnalysisRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetRcaByNcUseCaseTest {

    @Mock private RootCauseAnalysisRepository rcaRepository;

    private GetRcaByNcUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GetRcaByNcUseCase(rcaRepository);
    }

    @Test
    void shouldReturnRcaWhenExists() {
        UUID ncId = UUID.randomUUID();

        NonConformance nc = NonConformance.builder()
                .id(ncId)
                .title("NC")
                .status(NcStatus.IN_ANALYSIS)
                .reportedBy("op1")
                .reportedAt(LocalDateTime.now())
                .build();

        RootCauseAnalysis rca = RootCauseAnalysis.builder()
                .id(UUID.randomUUID())
                .nonConformance(nc)
                .why1("Por quê?")
                .answer1("Resposta")
                .rootCause("Causa identificada")
                .createdBy("supervisor1")
                .createdAt(LocalDateTime.now())
                .build();

        when(rcaRepository.findByNonConformanceId(ncId)).thenReturn(Optional.of(rca));

        RcaResponse response = useCase.execute(ncId);

        assertThat(response.ncId()).isEqualTo(ncId);
        assertThat(response.why1()).isEqualTo("Por quê?");
        assertThat(response.rootCause()).isEqualTo("Causa identificada");
    }

    @Test
    void shouldThrow404WhenRcaNotFound() {
        UUID ncId = UUID.randomUUID();
        when(rcaRepository.findByNonConformanceId(ncId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(ncId))
                .isInstanceOf(RcaNotFoundException.class);
    }
}
