package com.industrialhub.backend.production;

import com.industrialhub.backend.production.application.dto.PlanningSummaryRow;
import com.industrialhub.backend.production.application.usecase.GetPlanningSummaryUseCase;
import com.industrialhub.backend.production.domain.*;
import com.industrialhub.backend.production.infrastructure.MrpPlannedOrderRepository;
import com.industrialhub.backend.production.infrastructure.ProductionOrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GetPlanningSummaryUseCaseTest {

    @Mock ProductionOrderRepository orderRepository;
    @Mock MrpPlannedOrderRepository mrpRepository;
    @InjectMocks GetPlanningSummaryUseCase useCase;

    private static final LocalDate FROM = LocalDate.of(2026, 1, 1);
    private static final LocalDate TO   = LocalDate.of(2026, 1, 31);

    @Test
    void shouldCalculateEfficiency_whenPlannedQtyIsPositive() {
        // Given
        PlanningSummaryRow raw = new PlanningSummaryRow(
                "FAM-A", "Família A", "P001", "Produto X",
                100, 80, null, 0);
        when(orderRepository.findPlanningSummaryRaw(null, FROM, TO)).thenReturn(List.of(raw));
        when(mrpRepository.findAll()).thenReturn(List.of());

        // When
        List<PlanningSummaryRow> result = useCase.getSummary(null, FROM, TO);

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).efficiency()).isEqualTo(80.0); // 80/100 * 100 = 80.0
        assertThat(result.get(0).pendingMrpQty()).isEqualTo(0);
    }

    @Test
    void shouldReturnNullEfficiency_whenPlannedQtyIsZero() {
        // Given
        PlanningSummaryRow raw = new PlanningSummaryRow(
                "FAM-A", "Família A", "P001", "Produto X",
                0, 0, null, 0);
        when(orderRepository.findPlanningSummaryRaw(null, FROM, TO)).thenReturn(List.of(raw));
        when(mrpRepository.findAll()).thenReturn(List.of());

        // When
        List<PlanningSummaryRow> result = useCase.getSummary(null, FROM, TO);

        // Then
        assertThat(result.get(0).efficiency()).isNull(); // sem divisão por zero
    }

    @Test
    void shouldInjectPendingMrpQty_fromMrpSuggestions() {
        // Given
        PlanningSummaryRow raw = new PlanningSummaryRow(
                "FAM-A", "Família A", "P001", "Produto X",
                200, 150, null, 0);
        when(orderRepository.findPlanningSummaryRaw(null, FROM, TO)).thenReturn(List.of(raw));

        // Mock: 2 sugestões SUGGESTED para P001, total suggestedQty = 80
        Product p = new Product();
        p.setDynamicsCode("P001");

        MrpPlannedOrder s1 = new MrpPlannedOrder();
        s1.setProduct(p); s1.setStatus(MrpOrderStatus.SUGGESTED); s1.setSuggestedQty(50);
        MrpPlannedOrder s2 = new MrpPlannedOrder();
        s2.setProduct(p); s2.setStatus(MrpOrderStatus.ACCEPTED); s2.setSuggestedQty(30);
        // s3 já REJECTED — não deve contar
        MrpPlannedOrder s3 = new MrpPlannedOrder();
        s3.setProduct(p); s3.setStatus(MrpOrderStatus.REJECTED); s3.setSuggestedQty(10);

        when(mrpRepository.findAll()).thenReturn(List.of(s1, s2, s3));

        // When
        List<PlanningSummaryRow> result = useCase.getSummary(null, FROM, TO);

        // Then
        assertThat(result.get(0).pendingMrpQty()).isEqualTo(80); // 50 + 30 (REJECTED ignorado)
        assertThat(result.get(0).efficiency()).isCloseTo(75.0, org.assertj.core.data.Offset.offset(0.01)); // 150/200*100
    }

    /**
     * US-106 AC-1 — SEC-119: escapeCsv() neutraliza formula injection no CSV exportado.
     * Valores começando com '=', '+', '-', '@' recebem prefixo '\t' (tab) — inerte no Excel.
     */
    @Test
    void shouldNeutralizeFormulaInjection_inExportedCsv() {
        // familyName começa com '=' — simula injeção de fórmula
        // productName começa com '+' — segundo prefixo crítico
        PlanningSummaryRow row = new PlanningSummaryRow(
                "FAM-INJ", "=SUM(A1:B1)", "P999", "+Produto Malicioso",
                100, 80, 80.0, 0);
        when(orderRepository.findPlanningSummaryRaw(any(), any(), any())).thenReturn(List.of(row));
        when(mrpRepository.findAll()).thenReturn(List.of());

        ResponseEntity<byte[]> response = useCase.exportCsv(null, FROM, TO);

        String csv = new String(response.getBody(), StandardCharsets.UTF_8);
        // SEC-119: "=SUM(A1:B1)" → "\t=SUM(A1:B1)" (tab prefix)
        assertThat(csv).contains("\t=SUM(A1:B1)");
        // SEC-119: "+Produto Malicioso" → "\t+Produto Malicioso"
        assertThat(csv).contains("\t+Produto Malicioso");
        // Garantia: sem o prefixo bruto (célula não pode começar com = no CSV)
        assertThat(csv).doesNotContain(";=SUM");
        assertThat(csv).doesNotContain(";+Produto");
    }

    /**
     * US-106 AC-1 — SEC-119: prefixos '-' e '@' também são neutralizados.
     */
    @Test
    void shouldNeutralizeMinusAndAtPrefixes_inExportedCsv() {
        PlanningSummaryRow row = new PlanningSummaryRow(
                "FAM-AT", "-Família Negativa", "P998", "@Produto Arroba",
                50, 40, 80.0, 0);
        when(orderRepository.findPlanningSummaryRaw(any(), any(), any())).thenReturn(List.of(row));
        when(mrpRepository.findAll()).thenReturn(List.of());

        ResponseEntity<byte[]> response = useCase.exportCsv(null, FROM, TO);

        String csv = new String(response.getBody(), StandardCharsets.UTF_8);
        assertThat(csv).contains("\t-Família Negativa");
        assertThat(csv).contains("\t@Produto Arroba");
    }
}
