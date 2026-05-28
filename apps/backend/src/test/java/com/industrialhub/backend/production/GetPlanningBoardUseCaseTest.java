package com.industrialhub.backend.production;

import com.industrialhub.backend.production.application.dto.FamilyPlanningBoardResponse;
import com.industrialhub.backend.production.application.usecase.GetPlanningBoardUseCase;
import com.industrialhub.backend.production.domain.*;
import com.industrialhub.backend.production.infrastructure.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GetPlanningBoardUseCaseTest {

    @Mock ProductRepository productRepository;
    @Mock StockSnapshotRepository stockSnapshotRepository;
    @Mock ProductionOrderRepository productionOrderRepository;
    @Mock MrpPlannedOrderRepository mrpPlannedOrderRepository;
    @Mock ProductFamilyRepository productFamilyRepository;
    @InjectMocks GetPlanningBoardUseCase useCase;

    private ProductFamily makeFamily(String code) {
        ProductFamily fam = new ProductFamily();
        fam.setId(UUID.randomUUID());
        fam.setCode(code);
        fam.setName("Família " + code);
        return fam;
    }

    private Product makeProduct(ProductFamily family, String code, int minStock, int batchSize) {
        Product p = new Product();
        p.setId(UUID.randomUUID());
        p.setDynamicsCode(code);
        p.setName("Produto " + code);
        p.setType(ProductType.FINISHED);
        p.setActive(true);
        p.setFamily(family);
        p.setMinStockQty(minStock);
        p.setBatchSize(batchSize);
        return p;
    }

    private StockSnapshot makeStock(Product product, int qty) {
        StockSnapshot s = new StockSnapshot();
        s.setProduct(product);
        s.setQty(qty);
        s.setSnapshotDate(LocalDate.now());
        return s;
    }

    @Test
    void productWithSufficientStockShouldHaveStatusOk() {
        ProductFamily fam = makeFamily("FAM-A");
        Product product = makeProduct(fam, "P001", 100, 10);
        StockSnapshot stock = makeStock(product, 150); // stock > minStock

        when(productRepository.findAll()).thenReturn(List.of(product));
        when(stockSnapshotRepository.findLatestPerProduct()).thenReturn(List.of(stock));
        when(productionOrderRepository.sumOpenOrdersQtyForBoardByProduct(product.getId())).thenReturn(0);
        when(mrpPlannedOrderRepository.sumActiveSuggestedQtyByProduct(product.getId())).thenReturn(0);
        when(productionOrderRepository.sumPlannedPeopleByProduct(product.getId())).thenReturn(0);
        when(productionOrderRepository.countAndEarliestDueDateByProduct(product.getId()))
                .thenReturn(new Object[]{0L, null});

        List<FamilyPlanningBoardResponse> result = useCase.execute();

        assertThat(result).hasSize(1);
        var row = result.get(0).products().get(0);
        assertThat(row.planningStatus()).isEqualTo(FamilyPlanningBoardResponse.PlanningStatus.OK);
        assertThat(row.netNeed()).isZero();
    }

    @Test
    void productWithLowStockShouldHaveStatusAlert() {
        ProductFamily fam = makeFamily("FAM-B");
        Product product = makeProduct(fam, "P002", 100, 10);
        StockSnapshot stock = makeStock(product, 60); // netNeed = 40, minStock/2 = 50, so ALERT

        when(productRepository.findAll()).thenReturn(List.of(product));
        when(stockSnapshotRepository.findLatestPerProduct()).thenReturn(List.of(stock));
        when(productionOrderRepository.sumOpenOrdersQtyForBoardByProduct(product.getId())).thenReturn(0);
        when(mrpPlannedOrderRepository.sumActiveSuggestedQtyByProduct(product.getId())).thenReturn(0);
        when(productionOrderRepository.sumPlannedPeopleByProduct(product.getId())).thenReturn(0);
        when(productionOrderRepository.countAndEarliestDueDateByProduct(product.getId()))
                .thenReturn(new Object[]{0L, null});

        List<FamilyPlanningBoardResponse> result = useCase.execute();

        var row = result.get(0).products().get(0);
        assertThat(row.planningStatus()).isEqualTo(FamilyPlanningBoardResponse.PlanningStatus.ALERT);
        assertThat(row.netNeed()).isEqualTo(40);
    }

    @Test
    void productWithCriticalStockShouldHaveStatusCritical() {
        ProductFamily fam = makeFamily("FAM-C");
        Product product = makeProduct(fam, "P003", 100, 10);
        StockSnapshot stock = makeStock(product, 0); // netNeed = 100, minStock/2 = 50 → CRITICAL

        when(productRepository.findAll()).thenReturn(List.of(product));
        when(stockSnapshotRepository.findLatestPerProduct()).thenReturn(List.of(stock));
        when(productionOrderRepository.sumOpenOrdersQtyForBoardByProduct(product.getId())).thenReturn(0);
        when(mrpPlannedOrderRepository.sumActiveSuggestedQtyByProduct(product.getId())).thenReturn(0);
        when(productionOrderRepository.sumPlannedPeopleByProduct(product.getId())).thenReturn(0);
        when(productionOrderRepository.countAndEarliestDueDateByProduct(product.getId()))
                .thenReturn(new Object[]{0L, null});

        List<FamilyPlanningBoardResponse> result = useCase.execute();

        var row = result.get(0).products().get(0);
        assertThat(row.planningStatus()).isEqualTo(FamilyPlanningBoardResponse.PlanningStatus.CRITICAL);
    }

    @Test
    void productWithoutFamilyShouldBeExcluded() {
        Product product = makeProduct(null, "P004", 100, 10); // sem família

        when(productRepository.findAll()).thenReturn(List.of(product));
        when(stockSnapshotRepository.findLatestPerProduct()).thenReturn(List.of());

        List<FamilyPlanningBoardResponse> result = useCase.execute();

        assertThat(result).isEmpty();
    }
}
