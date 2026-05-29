package com.industrialhub.backend.production;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.industrialhub.backend.production.application.MrpCalculationService;
import com.industrialhub.backend.production.domain.*;
import com.industrialhub.backend.production.infrastructure.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * US-101 AC-6(b) — testes unitários para a lógica de explosão de BOM
 * no MrpCalculationService.explodePurchaseNeeds().
 */
@ExtendWith(MockitoExtension.class)
class MrpCalculationServiceTest {

    @Mock private ProductRepository productRepository;
    @Mock private StockSnapshotRepository stockSnapshotRepository;
    @Mock private ProductionOrderRepository productionOrderRepository;
    @Mock private MrpPlannedOrderRepository mrpPlannedOrderRepository;
    @Mock private ProductComponentRepository componentRepository;

    private MrpCalculationService service;

    @BeforeEach
    void setUp() {
        service = new MrpCalculationService(
                productRepository, stockSnapshotRepository,
                productionOrderRepository, mrpPlannedOrderRepository,
                componentRepository, new ObjectMapper()
        );
    }

    // ---------- helpers ----------

    private Product buildFinished(String code, int minStock) {
        ProductFamily family = new ProductFamily();
        family.setId(UUID.randomUUID());
        family.setCode("FAM-T");
        family.setName("Família Teste");

        Product p = new Product();
        p.setId(UUID.randomUUID());
        p.setDynamicsCode(code);
        p.setName("Produto " + code);
        p.setType(ProductType.FINISHED);
        p.setActive(true);
        p.setMinStockQty(minStock);
        p.setBatchSize(1);
        p.setLeadTimeDays(7);
        p.setFamily(family);
        return p;
    }

    private Product buildRaw(String code, String name) {
        Product p = new Product();
        p.setId(UUID.randomUUID());
        p.setDynamicsCode(code);
        p.setName(name);
        p.setType(ProductType.RAW_MATERIAL);
        p.setActive(true);
        return p;
    }

    private ProductComponent bomLink(Product parent, Product child, double qty, String unit) {
        return ProductComponent.builder()
                .id(UUID.randomUUID())
                .parentProduct(parent)
                .componentProduct(child)
                .quantity(qty)
                .unit(unit)
                .level(1)
                .active(true)
                .build();
    }

    // ---------- testes ----------

    /**
     * AC-6(b) — produto FINISHED com 2 RAW_MATERIAL no BOM:
     * purchaseNeeds devem ter quantidades reais (suggestedQty × bomItem.quantity, arredondado para cima).
     * stock=0, openOrders=0 → netNeed = minStockQty = 100, suggestedQty = 100.
     */
    @Test
    void shouldExplodeBom_withRealQuantities_forRawMaterialComponents() {
        Product finished = buildFinished("PROD-001", 100);
        Product rawA    = buildRaw("RAW-A", "Matéria A");
        Product rawB    = buildRaw("RAW-B", "Matéria B");

        when(productRepository.findAll()).thenReturn(List.of(finished));
        when(stockSnapshotRepository.findLatestPerProduct()).thenReturn(List.of()); // stock = 0
        when(productionOrderRepository.sumOpenOrdersQtyByProduct(any())).thenReturn(0);
        when(mrpPlannedOrderRepository.sumActiveSuggestedQtyByProduct(any())).thenReturn(0);
        when(componentRepository.findAllActive()).thenReturn(List.of(
                bomLink(finished, rawA, 2.0, "UN"),   // 100 * 2.0 = 200
                bomLink(finished, rawB, 0.5, "KG")    // 100 * 0.5 = 50
        ));

        MrpCalculationService.CalculationOutput output = service.calculate(true, "test-user");

        assertThat(output.purchaseNeeds()).hasSize(2);

        var needA = output.purchaseNeeds().stream()
                .filter(n -> "RAW-A".equals(n.productCode()))
                .findFirst().orElseThrow();
        assertThat(needA.quantity()).isEqualTo(200); // ceil(100 * 2.0)
        assertThat(needA.unit()).isEqualTo("UN");

        var needB = output.purchaseNeeds().stream()
                .filter(n -> "RAW-B".equals(n.productCode()))
                .findFirst().orElseThrow();
        assertThat(needB.quantity()).isEqualTo(50); // ceil(100 * 0.5)
        assertThat(needB.unit()).isEqualTo("KG");
    }

    /**
     * AC-6(b) — produto FINISHED SEM BOM cadastrado:
     * purchaseNeeds deve ser vazio (comportamento legado — ADR-044 Decision 3).
     * Sugestão MRP ainda é gerada normalmente.
     */
    @Test
    void shouldReturnEmptyPurchaseNeeds_whenNoBomRegistered() {
        Product finished = buildFinished("PROD-002", 50);

        when(productRepository.findAll()).thenReturn(List.of(finished));
        when(stockSnapshotRepository.findLatestPerProduct()).thenReturn(List.of());
        when(productionOrderRepository.sumOpenOrdersQtyByProduct(any())).thenReturn(0);
        when(mrpPlannedOrderRepository.sumActiveSuggestedQtyByProduct(any())).thenReturn(0);
        when(componentRepository.findAllActive()).thenReturn(List.of()); // sem BOM

        MrpCalculationService.CalculationOutput output = service.calculate(true, "test-user");

        // Sugestão criada (netNeed = 50), mas purchaseNeeds vazio
        assertThat(output.suggestions()).hasSize(1);
        assertThat(output.purchaseNeeds()).isEmpty();
    }

    /**
     * Componentes INTERMEDIATE são ignorados no purchaseNeeds (MVP nível 1 — ADR-044).
     * Apenas RAW_MATERIAL entram em purchaseNeeds.
     */
    @Test
    void shouldIgnoreIntermediateComponents_inPurchaseNeeds() {
        Product finished    = buildFinished("PROD-003", 60);
        Product rawA        = buildRaw("RAW-A", "Matéria A");

        Product intermediate = new Product();
        intermediate.setId(UUID.randomUUID());
        intermediate.setDynamicsCode("INTER-X");
        intermediate.setName("Intermediário X");
        intermediate.setType(ProductType.INTERMEDIATE);
        intermediate.setActive(true);

        when(productRepository.findAll()).thenReturn(List.of(finished));
        when(stockSnapshotRepository.findLatestPerProduct()).thenReturn(List.of());
        when(productionOrderRepository.sumOpenOrdersQtyByProduct(any())).thenReturn(0);
        when(mrpPlannedOrderRepository.sumActiveSuggestedQtyByProduct(any())).thenReturn(0);
        when(componentRepository.findAllActive()).thenReturn(List.of(
                bomLink(finished, rawA,        3.0, "UN"),  // RAW → incluído
                bomLink(finished, intermediate, 1.0, "UN")  // INTERMEDIATE → ignorado no MVP
        ));

        // suggestedQty = 60 (netNeed = 60, batchSize = 1)
        MrpCalculationService.CalculationOutput output = service.calculate(true, "test-user");

        assertThat(output.purchaseNeeds()).hasSize(1);
        assertThat(output.purchaseNeeds().get(0).productCode()).isEqualTo("RAW-A");
        assertThat(output.purchaseNeeds().get(0).quantity()).isEqualTo(180); // ceil(60 * 3.0)
    }

    // ===== US-105 — BOM nível 2 =====

    /**
     * AC-3(a): FINISHED → [INTERMEDIATE(1.0×) → [RAW-A(2.0×)]]
     * purchaseNeeds deve conter RAW-A com qty = ceil(suggestedQty * 1.0 * 2.0).
     */
    @Test
    void shouldExplodeLevel2Bom_whenIntermediateHasRawMaterialChildren() {
        Product finished     = buildFinished("PROD-L2", 50);
        Product intermediate = new Product();
        intermediate.setId(UUID.randomUUID());
        intermediate.setDynamicsCode("INTER-1");
        intermediate.setName("Intermediário 1");
        intermediate.setType(ProductType.INTERMEDIATE);
        intermediate.setActive(true);

        Product rawA = buildRaw("RAW-L2-A", "Matéria L2-A");

        // BOM nível 1: FINISHED → INTERMEDIATE (qty 1.0)
        ProductComponent l1 = bomLink(finished, intermediate, 1.0, "UN");
        // BOM nível 2: INTERMEDIATE → RAW_MATERIAL (qty 2.0)
        ProductComponent l2 = bomLink(intermediate, rawA, 2.0, "KG");

        when(productRepository.findAll()).thenReturn(List.of(finished));
        when(stockSnapshotRepository.findLatestPerProduct()).thenReturn(List.of());
        when(productionOrderRepository.sumOpenOrdersQtyByProduct(any())).thenReturn(0);
        when(mrpPlannedOrderRepository.sumActiveSuggestedQtyByProduct(any())).thenReturn(0);
        // bomByParent carrega AMBOS os níveis
        when(componentRepository.findAllActive()).thenReturn(List.of(l1, l2));

        // suggestedQty = 50 (netNeed = 50, batchSize = 1)
        MrpCalculationService.CalculationOutput output = service.calculate(true, "user");

        assertThat(output.purchaseNeeds()).hasSize(1);
        assertThat(output.purchaseNeeds().get(0).productCode()).isEqualTo("RAW-L2-A");
        assertThat(output.purchaseNeeds().get(0).quantity()).isEqualTo(100); // ceil(50 * 1.0 * 2.0)
        assertThat(output.purchaseNeeds().get(0).unit()).isEqualTo("KG");
    }

    /**
     * AC-3(b): FINISHED → [RAW-B(0.5×), INTERMEDIATE(1.0×) → [RAW-C(3.0×)]]
     * purchaseNeeds deve ter 2 entradas: RAW-B (nível 1) e RAW-C (nível 2).
     */
    @Test
    void shouldCombineLevel1AndLevel2PurchaseNeeds() {
        Product finished     = buildFinished("PROD-MIXED", 40);
        Product rawB         = buildRaw("RAW-B", "Matéria B");
        Product intermediate = new Product();
        intermediate.setId(UUID.randomUUID());
        intermediate.setDynamicsCode("INTER-2");
        intermediate.setName("Intermediário 2");
        intermediate.setType(ProductType.INTERMEDIATE);
        intermediate.setActive(true);
        Product rawC = buildRaw("RAW-C", "Matéria C");

        ProductComponent l1Raw   = bomLink(finished, rawB,        0.5, "UN"); // nível 1 direto
        ProductComponent l1Inter = bomLink(finished, intermediate, 1.0, "UN"); // nível 1 INTERMEDIATE
        ProductComponent l2Raw   = bomLink(intermediate, rawC,     3.0, "KG"); // nível 2

        when(productRepository.findAll()).thenReturn(List.of(finished));
        when(stockSnapshotRepository.findLatestPerProduct()).thenReturn(List.of());
        when(productionOrderRepository.sumOpenOrdersQtyByProduct(any())).thenReturn(0);
        when(mrpPlannedOrderRepository.sumActiveSuggestedQtyByProduct(any())).thenReturn(0);
        when(componentRepository.findAllActive()).thenReturn(List.of(l1Raw, l1Inter, l2Raw));

        // suggestedQty = 40
        MrpCalculationService.CalculationOutput output = service.calculate(true, "user");

        assertThat(output.purchaseNeeds()).hasSize(2);

        var needB = output.purchaseNeeds().stream().filter(n -> "RAW-B".equals(n.productCode())).findFirst().orElseThrow();
        assertThat(needB.quantity()).isEqualTo(20); // ceil(40 * 0.5)

        var needC = output.purchaseNeeds().stream().filter(n -> "RAW-C".equals(n.productCode())).findFirst().orElseThrow();
        assertThat(needC.quantity()).isEqualTo(120); // ceil(40 * 1.0 * 3.0)
    }

    /**
     * AC-3(c): INTERMEDIATE sem BOM cadastrado no nível 2 → nenhuma purchaseNeed adicional (sem NPE).
     */
    @Test
    void shouldNotThrow_whenIntermediateHasNoBomRegisteredAtLevel2() {
        Product finished     = buildFinished("PROD-INTER-EMPTY", 30);
        Product intermediate = new Product();
        intermediate.setId(UUID.randomUUID());
        intermediate.setDynamicsCode("INTER-NOBOM");
        intermediate.setName("Intermediário sem BOM");
        intermediate.setType(ProductType.INTERMEDIATE);
        intermediate.setActive(true);

        // Apenas o link de nível 1 — INTERMEDIATE sem filhos no bomByParent
        ProductComponent l1 = bomLink(finished, intermediate, 2.0, "UN");

        when(productRepository.findAll()).thenReturn(List.of(finished));
        when(stockSnapshotRepository.findLatestPerProduct()).thenReturn(List.of());
        when(productionOrderRepository.sumOpenOrdersQtyByProduct(any())).thenReturn(0);
        when(mrpPlannedOrderRepository.sumActiveSuggestedQtyByProduct(any())).thenReturn(0);
        when(componentRepository.findAllActive()).thenReturn(List.of(l1));

        MrpCalculationService.CalculationOutput output = service.calculate(true, "user");

        // INTERMEDIATE sem BOM registrado → nenhuma purchaseNeed, sem exceção
        assertThat(output.suggestions()).hasSize(1);
        assertThat(output.purchaseNeeds()).isEmpty();
    }
}
