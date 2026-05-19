package com.industrialhub.backend.common.alertthreshold;

import com.industrialhub.backend.common.application.AuditService;
import com.industrialhub.backend.common.application.usecase.AlertEvaluatorUseCase;
import com.industrialhub.backend.common.application.usecase.CreateAlertThresholdUseCase;
import com.industrialhub.backend.common.application.usecase.DeleteAlertThresholdUseCase;
import com.industrialhub.backend.common.application.usecase.GetAlertThresholdsUseCase;
import com.industrialhub.backend.common.application.usecase.UpdateAlertThresholdUseCase;
import com.industrialhub.backend.common.domain.AuditAction;
import com.industrialhub.backend.common.domain.EvaluateNowCooldownException;
import com.industrialhub.backend.common.presentation.AlertThresholdController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.security.Principal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlertThresholdControllerTest {

    @Mock private CreateAlertThresholdUseCase createUseCase;
    @Mock private GetAlertThresholdsUseCase getUseCase;
    @Mock private UpdateAlertThresholdUseCase updateUseCase;
    @Mock private DeleteAlertThresholdUseCase deleteUseCase;
    @Mock private AlertEvaluatorUseCase alertEvaluatorUseCase;
    @Mock private AuditService auditService;
    @Mock private Principal principal;

    private AlertThresholdController controller;

    @BeforeEach
    void setUp() {
        controller = new AlertThresholdController(
                createUseCase, getUseCase, updateUseCase, deleteUseCase,
                alertEvaluatorUseCase, auditService);
    }

    // (a) primeira chamada retorna 200 com quantidade avaliada
    @Test
    void evaluateNow_firstCall_shouldReturn200() {
        when(alertEvaluatorUseCase.execute()).thenReturn(5);
        when(principal.getName()).thenReturn("admin");

        ResponseEntity<Map<String, Integer>> response = controller.evaluateNow(principal);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("evaluated")).isEqualTo(5);
    }

    // (b) segunda chamada imediata retorna 429 (EvaluateNowCooldownException)
    @Test
    void evaluateNow_secondImmediateCall_shouldThrow429() {
        when(alertEvaluatorUseCase.execute()).thenReturn(3);
        when(principal.getName()).thenReturn("admin");

        // Primeira chamada — deve passar
        controller.evaluateNow(principal);

        // Segunda chamada imediata — deve lançar
        assertThatThrownBy(() -> controller.evaluateNow(principal))
                .isInstanceOf(EvaluateNowCooldownException.class)
                .hasMessageContaining("Avaliação manual disponível em");
    }

    // (c) audit log chamado com username correto
    @Test
    void evaluateNow_shouldCallAuditWithCorrectUsername() {
        when(alertEvaluatorUseCase.execute()).thenReturn(2);
        when(principal.getName()).thenReturn("admin_user");

        controller.evaluateNow(principal);

        verify(auditService).log(
                eq("admin_user"),
                eq(AuditAction.ALERT_EVALUATED_MANUAL),
                eq("AlertThreshold"),
                eq("all"),
                any());
    }
}
