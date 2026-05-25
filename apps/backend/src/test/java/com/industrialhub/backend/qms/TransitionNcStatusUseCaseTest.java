package com.industrialhub.backend.qms;

import com.industrialhub.backend.common.application.AuditService;
import com.industrialhub.backend.common.webhook.service.WebhookDispatchService;
import com.industrialhub.backend.qms.application.dto.NcResponse;
import com.industrialhub.backend.qms.application.usecase.TransitionNcStatusUseCase;
import com.industrialhub.backend.qms.domain.InvalidNcTransitionException;
import com.industrialhub.backend.qms.domain.NcNotFoundException;
import com.industrialhub.backend.qms.domain.NcSeverity;
import com.industrialhub.backend.qms.domain.NcStatus;
import com.industrialhub.backend.qms.domain.NcType;
import com.industrialhub.backend.qms.domain.NonConformance;
import com.industrialhub.backend.qms.infrastructure.NonConformanceRepository;
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
class TransitionNcStatusUseCaseTest {

    @Mock
    private NonConformanceRepository repository;

    @Mock
    private AuditService auditService;

    @Mock
    private WebhookDispatchService webhookDispatchService;

    private TransitionNcStatusUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new TransitionNcStatusUseCase(repository, auditService, webhookDispatchService);
    }

    private NonConformance buildNc(NcStatus status) {
        return NonConformance.builder()
                .id(UUID.randomUUID())
                .title("NC Teste")
                .type(NcType.PROCESS)
                .severity(NcSeverity.MEDIUM)
                .status(status)
                .reportedBy("operator1")
                .reportedAt(LocalDateTime.now())
                .build();
    }

    @Test
    void shouldTransitionOpenToInAnalysis() {
        NonConformance nc = buildNc(NcStatus.OPEN);
        when(repository.findById(nc.getId())).thenReturn(Optional.of(nc));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        NcResponse result = useCase.execute(nc.getId(), NcStatus.IN_ANALYSIS, "supervisor1");

        assertThat(result.status()).isEqualTo(NcStatus.IN_ANALYSIS);
        assertThat(result.closedAt()).isNull();
        assertThat(result.closedBy()).isNull();
    }

    @Test
    void shouldSetClosedAtAndClosedBy_whenTransitioningToClosed() {
        NonConformance nc = buildNc(NcStatus.IN_ANALYSIS);
        when(repository.findById(nc.getId())).thenReturn(Optional.of(nc));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        NcResponse result = useCase.execute(nc.getId(), NcStatus.CLOSED, "supervisor1");

        assertThat(result.status()).isEqualTo(NcStatus.CLOSED);
        assertThat(result.closedAt()).isNotNull();
        assertThat(result.closedBy()).isEqualTo("supervisor1");
    }

    @Test
    void shouldClearClosedFields_whenReopeningFromInAnalysis() {
        NonConformance nc = buildNc(NcStatus.IN_ANALYSIS);
        when(repository.findById(nc.getId())).thenReturn(Optional.of(nc));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        NcResponse result = useCase.execute(nc.getId(), NcStatus.OPEN, "supervisor1");

        assertThat(result.status()).isEqualTo(NcStatus.OPEN);
        assertThat(result.closedAt()).isNull();
        assertThat(result.closedBy()).isNull();
    }

    @Test
    void shouldThrowInvalidTransition_whenClosedToOpen() {
        NonConformance nc = buildNc(NcStatus.CLOSED);
        when(repository.findById(nc.getId())).thenReturn(Optional.of(nc));

        assertThatThrownBy(() -> useCase.execute(nc.getId(), NcStatus.OPEN, "supervisor1"))
                .isInstanceOf(InvalidNcTransitionException.class)
                .hasMessageContaining("CLOSED")
                .hasMessageContaining("OPEN");
    }

    @Test
    void shouldThrowInvalidTransition_whenOpenToClosed() {
        NonConformance nc = buildNc(NcStatus.OPEN);
        when(repository.findById(nc.getId())).thenReturn(Optional.of(nc));

        assertThatThrownBy(() -> useCase.execute(nc.getId(), NcStatus.CLOSED, "supervisor1"))
                .isInstanceOf(InvalidNcTransitionException.class);
    }

    @Test
    void shouldThrowNcNotFoundException_whenIdDoesNotExist() {
        UUID unknownId = UUID.randomUUID();
        when(repository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(unknownId, NcStatus.IN_ANALYSIS, "supervisor1"))
                .isInstanceOf(NcNotFoundException.class);
    }
}
