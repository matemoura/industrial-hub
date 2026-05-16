package com.industrialhub.backend.qms;

import com.industrialhub.backend.common.application.AuditService;
import com.industrialhub.backend.qms.application.dto.CreateRcaRequest;
import com.industrialhub.backend.qms.application.dto.RcaResponse;
import com.industrialhub.backend.qms.application.usecase.CreateRcaUseCase;
import com.industrialhub.backend.qms.domain.NcNotFoundException;
import com.industrialhub.backend.qms.domain.NcStatus;
import com.industrialhub.backend.qms.domain.NonConformance;
import com.industrialhub.backend.qms.domain.RcaAlreadyExistsException;
import com.industrialhub.backend.qms.domain.RcaNotAllowedException;
import com.industrialhub.backend.qms.domain.RootCauseAnalysis;
import com.industrialhub.backend.qms.infrastructure.NonConformanceRepository;
import com.industrialhub.backend.qms.infrastructure.RootCauseAnalysisRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreateRcaUseCaseTest {

    @Mock private NonConformanceRepository ncRepository;
    @Mock private RootCauseAnalysisRepository rcaRepository;
    @Mock private AuditService auditService;

    private CreateRcaUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new CreateRcaUseCase(ncRepository, rcaRepository, auditService);
    }

    private NonConformance buildNc(NcStatus status) {
        return NonConformance.builder()
                .id(UUID.randomUUID())
                .title("Peça fora de tolerância")
                .status(status)
                .reportedBy("op1")
                .reportedAt(LocalDateTime.now())
                .build();
    }

    private CreateRcaRequest buildRequest() {
        return new CreateRcaRequest(
                "Por que falhou?", "Falta de calibração",
                "Por que sem calibração?", "Processo ignorado",
                null, null, null, null, null, null, "Processo inadequado"
        );
    }

    @Test
    void shouldCreateRcaWhenNcIsInAnalysis() {
        NonConformance nc = buildNc(NcStatus.IN_ANALYSIS);
        UUID ncId = nc.getId();

        when(ncRepository.findById(ncId)).thenReturn(Optional.of(nc));
        when(rcaRepository.existsByNonConformanceId(ncId)).thenReturn(false);

        RootCauseAnalysis saved = RootCauseAnalysis.builder()
                .id(UUID.randomUUID())
                .nonConformance(nc)
                .why1("Por que falhou?")
                .answer1("Falta de calibração")
                .why2("Por que sem calibração?")
                .answer2("Processo ignorado")
                .rootCause("Processo inadequado")
                .createdBy("supervisor1")
                .createdAt(LocalDateTime.now())
                .build();

        when(rcaRepository.save(any(RootCauseAnalysis.class))).thenReturn(saved);

        RcaResponse response = useCase.execute(ncId, buildRequest(), "supervisor1");

        assertThat(response.why1()).isEqualTo("Por que falhou?");
        assertThat(response.rootCause()).isEqualTo("Processo inadequado");
        assertThat(response.createdBy()).isEqualTo("supervisor1");

        ArgumentCaptor<RootCauseAnalysis> captor = ArgumentCaptor.forClass(RootCauseAnalysis.class);
        verify(rcaRepository).save(captor.capture());
        assertThat(captor.getValue().getCreatedAt()).isNotNull();
        assertThat(captor.getValue().getUpdatedAt()).isNull();
    }

    @Test
    void shouldCreateRcaWhenNcIsClosed() {
        NonConformance nc = buildNc(NcStatus.CLOSED);
        UUID ncId = nc.getId();

        when(ncRepository.findById(ncId)).thenReturn(Optional.of(nc));
        when(rcaRepository.existsByNonConformanceId(ncId)).thenReturn(false);

        RootCauseAnalysis saved = RootCauseAnalysis.builder()
                .id(UUID.randomUUID())
                .nonConformance(nc)
                .why1("Por que?")
                .createdBy("supervisor1")
                .createdAt(LocalDateTime.now())
                .build();

        when(rcaRepository.save(any())).thenReturn(saved);

        RcaResponse response = useCase.execute(ncId, buildRequest(), "supervisor1");

        assertThat(response).isNotNull();
    }

    @Test
    void shouldThrow422WhenNcIsOpen() {
        NonConformance nc = buildNc(NcStatus.OPEN);
        UUID ncId = nc.getId();

        when(ncRepository.findById(ncId)).thenReturn(Optional.of(nc));

        assertThatThrownBy(() -> useCase.execute(ncId, buildRequest(), "supervisor1"))
                .isInstanceOf(RcaNotAllowedException.class)
                .hasMessageContaining("RCA só pode ser criada após início da análise");
    }

    @Test
    void shouldThrow409WhenRcaAlreadyExists() {
        NonConformance nc = buildNc(NcStatus.IN_ANALYSIS);
        UUID ncId = nc.getId();

        when(ncRepository.findById(ncId)).thenReturn(Optional.of(nc));
        when(rcaRepository.existsByNonConformanceId(ncId)).thenReturn(true);

        assertThatThrownBy(() -> useCase.execute(ncId, buildRequest(), "supervisor1"))
                .isInstanceOf(RcaAlreadyExistsException.class);
    }

    @Test
    void shouldThrow404WhenNcNotFound() {
        UUID ncId = UUID.randomUUID();
        when(ncRepository.findById(ncId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(ncId, buildRequest(), "supervisor1"))
                .isInstanceOf(NcNotFoundException.class);
    }
}
