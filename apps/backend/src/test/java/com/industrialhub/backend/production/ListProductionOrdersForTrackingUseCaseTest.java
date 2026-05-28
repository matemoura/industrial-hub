package com.industrialhub.backend.production;

import com.industrialhub.backend.production.application.dto.ProductionOrderListResponse;
import com.industrialhub.backend.production.application.usecase.ListProductionOrdersForTrackingUseCase;
import com.industrialhub.backend.production.domain.*;
import com.industrialhub.backend.production.infrastructure.ImportProductionBatchRepository;
import com.industrialhub.backend.production.infrastructure.ProductionOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * US-082 AC#6 / AC#8 — ListProductionOrdersForTrackingUseCase unit tests.
 * Cobre: paginação, filtro por displayStatus, filtro overdue, campo lastSyncAt.
 */
@ExtendWith(MockitoExtension.class)
class ListProductionOrdersForTrackingUseCaseTest {

    @Mock
    private ProductionOrderRepository orderRepository;

    @Mock
    private ImportProductionBatchRepository batchRepository;

    private ListProductionOrdersForTrackingUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ListProductionOrdersForTrackingUseCase(orderRepository, batchRepository);
        when(batchRepository.findFirstByTypeOrderByImportedAtDesc(ProductionImportType.PRODUCTION_ORDERS))
                .thenReturn(Optional.empty());
    }

    // -----------------------------------------------------------------------
    // AC#6: GET /tracking/orders retorna lista paginada de ordens
    // -----------------------------------------------------------------------

    @Test
    void shouldReturnPaginatedOrders_withLastSyncAt() {
        // Arrange
        ProductionOrder order = buildOrder("OP-001", ProductionOrderStatus.PLANNED,
                LocalDate.now().plusDays(3));
        Pageable pageable = PageRequest.of(0, 50);

        when(orderRepository.findFiltered(isNull(), isNull(), isNull(), eq(false), any(), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(order), pageable, 1));

        LocalDateTime syncAt = LocalDateTime.now().minusHours(1);
        when(batchRepository.findFirstByTypeOrderByImportedAtDesc(ProductionImportType.PRODUCTION_ORDERS))
                .thenReturn(Optional.of(ImportProductionBatch.builder()
                        .id(UUID.randomUUID())
                        .type(ProductionImportType.PRODUCTION_ORDERS)
                        .importedAt(syncAt)
                        .build()));

        // Act
        ProductionOrderListResponse result = useCase.execute(null, null, null, null, pageable);

        // Assert
        assertThat(result.content()).hasSize(1);
        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(result.number()).isEqualTo(0);
        assertThat(result.lastSyncAt()).isEqualTo(syncAt);
        assertThat(result.content().get(0).dynamicsOrderNumber()).isEqualTo("OP-001");
    }

    // -----------------------------------------------------------------------
    // AC#8: Filtro por displayStatus filtra resultados
    // -----------------------------------------------------------------------

    @Test
    void shouldFilterByDisplayStatus_whenProvided() {
        // Arrange — repository returns both PLANNED and IN_PROGRESS orders
        ProductionOrder planned = buildOrder("OP-PLAN", ProductionOrderStatus.PLANNED,
                LocalDate.now().plusDays(5));
        ProductionOrder inProgress = buildOrder("OP-PROG", ProductionOrderStatus.IN_PROGRESS,
                LocalDate.now().plusDays(2));

        Pageable pageable = PageRequest.of(0, 50);
        when(orderRepository.findFiltered(isNull(), isNull(), isNull(), eq(false), any(), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(planned, inProgress), pageable, 2));

        // Act — filter only IN_PROGRESS
        ProductionOrderListResponse result = useCase.execute(null, "IN_PROGRESS", null, null, pageable);

        // Assert — only IN_PROGRESS order returned
        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).dynamicsOrderNumber()).isEqualTo("OP-PROG");
        assertThat(result.content().get(0).displayStatus())
                .isEqualTo(ProductionOrderDisplayStatus.IN_PROGRESS);
    }

    // -----------------------------------------------------------------------
    // AC#8: Filtro overdue=true passa overdueOnly=true para o repository
    // -----------------------------------------------------------------------

    @Test
    void shouldPassOverdueTrueToRepository_whenFilterRequested() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 50);
        when(orderRepository.findFiltered(isNull(), isNull(), isNull(), eq(true), any(), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        // Act
        ProductionOrderListResponse result = useCase.execute(null, null, true, null, pageable);

        // Assert
        assertThat(result.content()).isEmpty();
        assertThat(result.totalElements()).isEqualTo(0);
        verify(orderRepository).findFiltered(isNull(), isNull(), isNull(), eq(true), any(), eq(pageable));
    }

    // -----------------------------------------------------------------------
    // AC#8: displayStatus desconhecido é tratado como sem filtro
    // -----------------------------------------------------------------------

    @Test
    void shouldIgnoreUnknownDisplayStatus() {
        // Arrange
        ProductionOrder order = buildOrder("OP-XYZ", ProductionOrderStatus.PLANNED,
                LocalDate.now().plusDays(1));
        Pageable pageable = PageRequest.of(0, 50);

        when(orderRepository.findFiltered(isNull(), isNull(), isNull(), eq(false), any(), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(order), pageable, 1));

        // Act — unknown display status should not crash and should return unfiltered
        ProductionOrderListResponse result = useCase.execute(null, "TOTALLY_INVALID_STATUS",
                null, null, pageable);

        // Assert — no filter applied, order present
        assertThat(result.content()).hasSize(1);
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private ProductionOrder buildOrder(String orderNumber, ProductionOrderStatus status, LocalDate dueDate) {
        Product product = Product.builder()
                .id(UUID.randomUUID())
                .dynamicsCode("P-TEST")
                .name("Produto Teste")
                .type(ProductType.FINISHED)
                .build();

        return ProductionOrder.builder()
                .id(UUID.randomUUID())
                .dynamicsOrderNumber(orderNumber)
                .product(product)
                .status(status)
                .plannedQty(BigDecimal.valueOf(100))
                .producedQty(BigDecimal.valueOf(40))
                .dueDate(dueDate)
                .importedAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }
}
