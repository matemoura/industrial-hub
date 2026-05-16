package com.industrialhub.backend.qms;

import com.industrialhub.backend.common.application.AuditService;
import com.industrialhub.backend.qms.application.dto.CreateRcaRequest;
import com.industrialhub.backend.qms.application.dto.RcaResponse;
import com.industrialhub.backend.qms.application.usecase.UpdateRcaUseCase;
import com.industrialhub.backend.qms.domain.NcStatus;
import com.industrialhub.backend.qms.domain.NonConformance;
import com.industrialhub.backend.qms.domain.RcaNotFoundException;
import com.industrialhub.backend.qms.domain.RcaNotAllowedException;
import com.industrialhub.backend.qms.domain.RootCauseAnalysis;
import com.industrialhub.backend.qms.infrastructure.NonConformanceRepository;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UpdateRcaUseCaseTest {

    @Mock private NonConformanceRepository ncRepository;
    @Mock private RootCauseAnalysisRepository rcaRepository;
    @Mock private AuditService auditService;

    private UpdateRcaUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new UpdateRcaUseCase(ncRepository, rcaRepository, auditService);
    }

    private NonConformance buildNc(NcStatus status) {
        return NonConformance.builder()
                .id(UUID.randomUUID())
                .title("NC")
                .status(status)
                .reportedBy("op1")
                .reportedAt(LocalDateTime.now())
                .build();
    }

    @Test
    void shouldUpdateRcaWhenNcIsInAnalysis() {
        NonConformance nc = buildNc(NcStatus.IN_ANALYSIS);
        UUID ncId = nc.getId();

        RootCauseAnalysis existing = RootCauseAnalysis.builder()
                .id(UUID.randomUUID())
                .nonConformance(nc)
                .why1("Antigo porquê")
                .createdBy("supervisor1")
                .createdAt(LocalDateTime.now().minusDays(1))
                .build();

        when(ncRepository.findById(ncId)).thenReturn(Optional.of(nc));
        when(rcaRepository.findByNonConformanceId(ncId)).thenReturn(Optional.of(existing));
        when(rcaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CreateRcaRequest request = new CreateRcaRequest(
                "Novo porquê", "Nova resposta",
                null, null, null, null, null, null, null, null, "Nova causa raiz"
        );

        RcaResponse response = useCase.execute(ncId, request, "supervisor1");

        assertThat(response.why1()).isEqualTo("Novo porquê");
        assertThat(response.answer1()).isEqualTo("Nova resposta");
        assertThat(response.rootCause()).isEqualTo("Nova causa raiz");
        assertThat(response.updatedAt()).isNotNull();
    }

    @Test
    void shouldThrow422WhenNcIsClosed() {
        NonConformance nc = buildNc(NcStatus.CLOSED);
        UUID ncId = nc.getId();

        when(ncRepository.findById(ncId)).thenReturn(Optional.of(nc));

        CreateRcaRequest request = new CreateRcaRequest(
                "Por quê?", null, null, null, null, null, null, null, null, null, null
        );

        assertThatThrownBy(() -> useCase.execute(ncId, request, "supervisor1"))
                .isInstanceOf(RcaNotAllowedException.class)
                .hasMessageContaining("fechamento");
    }

    @Test
    void shouldThrow404WhenRcaDoesNotExist() {
        NonConformance nc = buildNc(NcStatus.IN_ANALYSIS);
        UUID ncId = nc.getId();

        when(ncRepository.findById(ncId)).thenReturn(Optional.of(nc));
        when(rcaRepository.findByNonConformanceId(ncId)).thenReturn(Optional.empty());

        CreateRcaRequest request = new CreateRcaRequest(
                "Por quê?", null, null, null, null, null, null, null, null, null, null
        );

        assertThatThrownBy(() -> useCase.execute(ncId, request, "supervisor1"))
                .isInstanceOf(RcaNotFoundException.class);
    }
}
