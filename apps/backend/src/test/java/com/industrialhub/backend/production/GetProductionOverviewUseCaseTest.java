package com.industrialhub.backend.production;

import com.industrialhub.backend.production.application.dto.ProductionOverviewDto;
import com.industrialhub.backend.production.application.usecase.GetProductionOverviewUseCase;
import com.industrialhub.backend.production.domain.*;
import com.industrialhub.backend.production.infrastructure.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * US-104 — testes unitários do painel executivo de produção.
 */
@ExtendWith(MockitoExtension.class)
class GetProductionOverviewUseCaseTest {

    @Mock private ProductRepository productRepository;
    @Mock private ProductComponentRepository componentRepository;
    @Mock private MrpPlannedOrderRepository mrpRepository;
    @Mock private ProductionOrderRepository orderRepository;

    @InjectMocks private GetProductionOverviewUseCase useCase;

    private Product buildFinishedProduct(String code) {
        Product p = new Product();
        p.setId(UUID.randomUUID());
        p.setDynamicsCode(code);
        p.setType(ProductType.FINISHED);
        p.setActive(true);
        return p;
    }

    /** AC-3(a): coveragePct é null quando não há produtos FINISHED ativos. */
    @Test
    void shouldReturnNullCoveragePct_whenNoFinishedProducts() {
        when(productRepository.findAll()).thenReturn(List.of());
        when(componentRepository.findAllActive()).thenReturn(List.of());
        when(mrpRepository.findAll()).thenReturn(List.of());
        when(orderRepository.findDoneOrdersInPeriod(any(), any())).thenReturn(List.of());
        when(orderRepository.findAll()).thenReturn(List.of());

        ProductionOverviewDto dto = useCase.getOverview();

        assertThat(dto.bomCoverage().totalFinishedProducts()).isEqualTo(0);
        assertThat(dto.bomCoverage().coveragePct()).isNull();
    }

    /** AC-3(b): fulfillmentPct ignora sugestões REJECTED no cálculo do denominador. */
    @Test
    void shouldIgnoreRejected_inMrpFulfillmentPct() {
        when(productRepository.findAll()).thenReturn(List.of());
        when(componentRepository.findAllActive()).thenReturn(List.of());
        when(orderRepository.findDoneOrdersInPeriod(any(), any())).thenReturn(List.of());
        when(orderRepository.findAll()).thenReturn(List.of());

        // 3 sugestões: 1 ACCEPTED, 1 REJECTED, 1 SUGGESTED
        // fulfillmentPct = 1 / (3 - 1) * 100 = 50.0
        Product p = buildFinishedProduct("P001");
        MrpPlannedOrder accepted = new MrpPlannedOrder();
        accepted.setProduct(p); accepted.setStatus(MrpOrderStatus.ACCEPTED); accepted.setSuggestedQty(10);

        MrpPlannedOrder rejected = new MrpPlannedOrder();
        rejected.setProduct(p); rejected.setStatus(MrpOrderStatus.REJECTED); rejected.setSuggestedQty(10);

        MrpPlannedOrder pending = new MrpPlannedOrder();
        pending.setProduct(p); pending.setStatus(MrpOrderStatus.SUGGESTED); pending.setSuggestedQty(10);

        when(mrpRepository.findAll()).thenReturn(List.of(accepted, rejected, pending));

        ProductionOverviewDto dto = useCase.getOverview();

        assertThat(dto.mrpFulfillment().totalSuggestions()).isEqualTo(3);
        assertThat(dto.mrpFulfillment().rejected()).isEqualTo(1);
        assertThat(dto.mrpFulfillment().accepted()).isEqualTo(1);
        assertThat(dto.mrpFulfillment().pending()).isEqualTo(1);
        assertThat(dto.mrpFulfillment().fulfillmentPct()).isEqualTo(50.0);
    }

    /** AC-3(c): efficiencyTrend está vazio quando não há OPs DONE no período. */
    @Test
    void shouldReturnEmptyTrend_whenNoDoneOrdersInPeriod() {
        when(productRepository.findAll()).thenReturn(List.of());
        when(componentRepository.findAllActive()).thenReturn(List.of());
        when(mrpRepository.findAll()).thenReturn(List.of());
        when(orderRepository.findDoneOrdersInPeriod(any(), any())).thenReturn(List.of());
        when(orderRepository.findAll()).thenReturn(List.of());

        ProductionOverviewDto dto = useCase.getOverview();

        assertThat(dto.efficiencyTrend()).isEmpty();
    }

    /** Bonus: efficiencyTrend calculado corretamente para OPs DONE com plannedQty > 0. */
    @Test
    void shouldCalculateEfficiencyTrend_forDoneOrders() {
        when(productRepository.findAll()).thenReturn(List.of());
        when(componentRepository.findAllActive()).thenReturn(List.of());
        when(mrpRepository.findAll()).thenReturn(List.of());
        when(orderRepository.findAll()).thenReturn(List.of());

        LocalDate date = LocalDate.of(2026, 6, 1);
        ProductionOrder op1 = new ProductionOrder();
        op1.setId(UUID.randomUUID());
        op1.setStatus(ProductionOrderStatus.DONE);
        op1.setPlannedQty(BigDecimal.valueOf(100));
        op1.setProducedQty(BigDecimal.valueOf(80));
        op1.setDueDate(date);

        ProductionOrder op2 = new ProductionOrder();
        op2.setId(UUID.randomUUID());
        op2.setStatus(ProductionOrderStatus.DONE);
        op2.setPlannedQty(BigDecimal.valueOf(100));
        op2.setProducedQty(BigDecimal.valueOf(60));
        op2.setDueDate(date);

        when(orderRepository.findDoneOrdersInPeriod(any(), any())).thenReturn(List.of(op1, op2));

        ProductionOverviewDto dto = useCase.getOverview();

        assertThat(dto.efficiencyTrend()).hasSize(1);
        assertThat(dto.efficiencyTrend().get(0).date()).isEqualTo(date);
        // avg(80%, 60%) = 70%
        assertThat(dto.efficiencyTrend().get(0).avgEfficiency())
                .isEqualTo(70.0);
    }
}
