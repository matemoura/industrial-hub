package com.industrialhub.backend.qms.risk;

import com.industrialhub.backend.common.application.AuditService;
import com.industrialhub.backend.qms.risk.application.dto.CreateRiskItemRequest;
import com.industrialhub.backend.qms.risk.application.dto.RiskItemResponse;
import com.industrialhub.backend.qms.risk.application.usecase.CreateRiskItemUseCase;
import com.industrialhub.backend.qms.risk.domain.RiskItem;
import com.industrialhub.backend.qms.risk.domain.RiskLevel;
import com.industrialhub.backend.qms.risk.infrastructure.RiskItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreateRiskItemUseCaseTest {

    @Mock private RiskItemRepository riskItemRepository;
    @Mock private AuditService auditService;

    private CreateRiskItemUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new CreateRiskItemUseCase(riskItemRepository, auditService);
    }

    private CreateRiskItemRequest buildRequest(int severity, int occurrence, int detectability) {
        return new CreateRiskItemRequest(
            "Processo de Esterilização",
            "Falha no ciclo",
            "Produto não esterilizado",
            "Falha na válvula",
            severity, occurrence, detectability,
            "Engenheiro João",
            null, null
        );
    }

    private void stubSave() {
        when(riskItemRepository.save(any())).thenAnswer(inv -> {
            RiskItem item = inv.getArgument(0);
            item.setId(UUID.randomUUID());
            item.setCreatedAt(LocalDateTime.now());
            return item;
        });
    }

    @Test
    void shouldCalculateRpn_severity5_occurrence4_detectability3() {
        // AC (a): severity=5, occurrence=4, detectability=3 → rpn=60
        stubSave();
        RiskItemResponse response = useCase.execute(buildRequest(5, 4, 3), "supervisor1");
        assertThat(response.rpn()).isEqualTo(60);
    }

    @Test
    void shouldSetRiskLevel_MEDIUM_forRpn60() {
        // AC (b): riskLevel=MEDIUM para RPN=60 (31–100)
        stubSave();
        RiskItemResponse response = useCase.execute(buildRequest(5, 4, 3), "supervisor1");
        assertThat(response.riskLevel()).isEqualTo(RiskLevel.MEDIUM);
    }

    @Test
    void shouldSetRiskLevel_LOW_forRpn1() {
        // AC (c): riskLevel=LOW para RPN≤30 (1×1×1=1)
        stubSave();
        RiskItemResponse response = useCase.execute(buildRequest(1, 1, 1), "supervisor1");
        assertThat(response.rpn()).isEqualTo(1);
        assertThat(response.riskLevel()).isEqualTo(RiskLevel.LOW);
    }

    @Test
    void shouldSetRiskLevel_HIGH_forRpn200() {
        // AC (d): riskLevel=HIGH para RPN=200 (5×5×8=200)
        stubSave();
        RiskItemResponse response = useCase.execute(buildRequest(5, 5, 8), "supervisor1");
        assertThat(response.rpn()).isEqualTo(200);
        assertThat(response.riskLevel()).isEqualTo(RiskLevel.HIGH);
    }

    @Test
    void shouldSetRiskLevel_CRITICAL_forRpnAbove200() {
        // AC (e): riskLevel=CRITICAL para RPN>200 (5×5×9=225)
        stubSave();
        RiskItemResponse response = useCase.execute(buildRequest(5, 5, 9), "supervisor1");
        assertThat(response.rpn()).isEqualTo(225);
        assertThat(response.riskLevel()).isEqualTo(RiskLevel.CRITICAL);
    }
}
