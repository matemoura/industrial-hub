package com.industrialhub.backend.production;

import com.industrialhub.backend.production.application.dto.ProductionTrackingResponse;
import com.industrialhub.backend.production.application.usecase.GetProductionTrackingUseCase;
import com.industrialhub.backend.production.domain.ImportProductionBatch;
import com.industrialhub.backend.production.domain.ProductionImportType;
import com.industrialhub.backend.production.domain.ProductionOrderDisplayStatus;
import com.industrialhub.backend.production.domain.ProductionOrderStatus;
import com.industrialhub.backend.production.infrastructure.ImportProductionBatchRepository;
import com.industrialhub.backend.production.infrastructure.ProductionOrderRepository;
import com.industrialhub.backend.production.infrastructure.ProductionOrderTrackingView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * US-082 — GetProductionTrackingUseCase unit tests.
 * Cenários: agrupamento por status, filtro por família, truncated flag, DONE recentes vs antigas.
 */
@ExtendWith(MockitoExtension.class)
class GetProductionTrackingUseCaseTest {

    @Mock
    private ProductionOrderRepository orderRepository;

    @Mock
    private ImportProductionBatchRepository batchRepository;

    private GetProductionTrackingUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GetProductionTrackingUseCase(orderRepository, batchRepository);
        when(batchRepository.findFirstByTypeOrderByImportedAtDesc(ProductionImportType.PRODUCTION_ORDERS))
                .thenReturn(Optional.empty());
    }

    // -----------------------------------------------------------------------
    // Cenário 1: Retorna colunas agrupadas por status
    // -----------------------------------------------------------------------

    @Test
    void shouldGroupOrdersByDisplayStatus() {
        List<ProductionOrderTrackingView> rows = List.of(
                mockView("1", "OP-001", "Produto A", "FAM-1", ProductionOrderStatus.PLANNED,
                        BigDecimal.TEN, BigDecimal.ZERO, LocalDate.now().plusDays(5)),
                mockView("2", "OP-002", "Produto B", "FAM-1", ProductionOrderStatus.IN_PROGRESS,
                        BigDecimal.TEN, BigDecimal.valueOf(5), LocalDate.now().plusDays(2)),
                mockView("3", "OP-003", "Produto C", "FAM-1", ProductionOrderStatus.PLANNED,
                        BigDecimal.valueOf(20), BigDecimal.ZERO, LocalDate.now().plusDays(10))
        );

        when(orderRepository.findForTracking(isNull(), any())).thenReturn(rows);

        ProductionTrackingResponse result = useCase.execute(null, null);

        assertThat(result.columns()).isNotEmpty();

        var plannedCol = result.columns().stream()
                .filter(c -> c.status() == ProductionOrderDisplayStatus.PLANNED)
                .findFirst().orElseThrow();
        assertThat(plannedCol.items()).hasSize(2);

        var inProgressCol = result.columns().stream()
                .filter(c -> c.status() == ProductionOrderDisplayStatus.IN_PROGRESS)
                .findFirst().orElseThrow();
        assertThat(inProgressCol.items()).hasSize(1);
        assertThat(inProgressCol.items().get(0).dynamicsOrderNumber()).isEqualTo("OP-002");
    }

    // -----------------------------------------------------------------------
    // Cenário 2: Filtra por familyCode — apenas OPs da família informada
    // -----------------------------------------------------------------------

    @Test
    void shouldDelegateFilterToRepository_whenFamilyCodeProvided() {
        List<ProductionOrderTrackingView> rows = List.of(
                mockView("1", "OP-010", "Produto X", "FAM-A", ProductionOrderStatus.IN_PROGRESS,
                        BigDecimal.TEN, BigDecimal.ZERO, LocalDate.now().plusDays(3))
        );

        when(orderRepository.findForTracking(eq("FAM-A"), any())).thenReturn(rows);

        ProductionTrackingResponse result = useCase.execute("FAM-A", null);

        verify(orderRepository).findForTracking(eq("FAM-A"), any());

        long totalItems = result.columns().stream().mapToLong(c -> c.items().size()).sum();
        assertThat(totalItems).isEqualTo(1);
    }

    // -----------------------------------------------------------------------
    // Cenário 3: Flag truncated quando coluna tem >50 OPs
    // -----------------------------------------------------------------------

    @Test
    void shouldSetTruncatedFlag_whenColumnExceeds50Items() {
        List<ProductionOrderTrackingView> rows = new ArrayList<>();
        for (int i = 1; i <= 55; i++) {
            rows.add(mockView(String.valueOf(i), "OP-" + i, "Produto " + i, "FAM-1",
                    ProductionOrderStatus.PLANNED, BigDecimal.TEN, BigDecimal.ZERO,
                    LocalDate.now().plusDays(i)));
        }

        when(orderRepository.findForTracking(isNull(), any())).thenReturn(rows);

        ProductionTrackingResponse result = useCase.execute(null, null);

        var plannedCol = result.columns().stream()
                .filter(c -> c.status() == ProductionOrderDisplayStatus.PLANNED)
                .findFirst().orElseThrow();

        assertThat(plannedCol.truncated()).isTrue();
        assertThat(plannedCol.total()).isEqualTo(55);
        assertThat(plannedCol.items()).hasSize(50);
    }

    // -----------------------------------------------------------------------
    // Cenário 4: DONE recentes aparecem em coluna DONE; antigas excluídas pelo repo
    // -----------------------------------------------------------------------

    @Test
    void shouldIncludeDoneOrders_whenReturnedByRepository() {
        // The repository already filters DONE older than weekStart — use case trusts the repo
        List<ProductionOrderTrackingView> rows = List.of(
                mockView("99", "OP-DONE", "Produto Done", "FAM-1", ProductionOrderStatus.DONE,
                        BigDecimal.TEN, BigDecimal.TEN, LocalDate.now().minusDays(1))
        );

        when(orderRepository.findForTracking(isNull(), any())).thenReturn(rows);

        ProductionTrackingResponse result = useCase.execute(null, null);

        var doneCol = result.columns().stream()
                .filter(c -> c.status() == ProductionOrderDisplayStatus.DONE)
                .findFirst().orElseThrow();

        assertThat(doneCol.items()).hasSize(1);
        assertThat(doneCol.items().get(0).dynamicsOrderNumber()).isEqualTo("OP-DONE");
        // DONE items should NOT be marked overdue
        assertThat(doneCol.items().get(0).overdue()).isFalse();
    }

    // -----------------------------------------------------------------------
    // Helper — creates a minimal stub of ProductionOrderTrackingView
    // -----------------------------------------------------------------------

    private ProductionOrderTrackingView mockView(String id, String orderNumber, String productName,
                                                   String familyName, ProductionOrderStatus status,
                                                   BigDecimal plannedQty, BigDecimal producedQty,
                                                   LocalDate dueDate) {
        return new ProductionOrderTrackingView() {
            @Override public String getId() { return id; }
            @Override public String getDynamicsOrderNumber() { return orderNumber; }
            @Override public String getProductName() { return productName; }
            @Override public String getProductFamilyName() { return familyName; }
            @Override public ProductionOrderStatus getStatus() { return status; }
            @Override public BigDecimal getPlannedQty() { return plannedQty; }
            @Override public BigDecimal getProducedQty() { return producedQty; }
            @Override public LocalDate getDueDate() { return dueDate; }
        };
    }
}
