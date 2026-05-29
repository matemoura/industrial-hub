package com.industrialhub.backend.production.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.industrialhub.backend.production.domain.*;
import com.industrialhub.backend.production.infrastructure.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.UUID;

/**
 * ADR-043 Decisão 2 — helper stateless compartilhado por RunMrpUseCase e DryRunMrpUseCase.
 * Sem @Transactional própria: dry-run apenas lê (repos têm readOnly por default),
 * run é envolvido pelo @Transactional do RunMrpUseCase.
 */
@Service
public class MrpCalculationService {

    private static final Logger log = LoggerFactory.getLogger(MrpCalculationService.class);

    private final ProductRepository productRepository;
    private final StockSnapshotRepository stockSnapshotRepository;
    private final ProductionOrderRepository productionOrderRepository;
    private final MrpPlannedOrderRepository mrpPlannedOrderRepository;
    private final ProductComponentRepository componentRepository;
    private final ObjectMapper objectMapper;

    public MrpCalculationService(
            ProductRepository productRepository,
            StockSnapshotRepository stockSnapshotRepository,
            ProductionOrderRepository productionOrderRepository,
            MrpPlannedOrderRepository mrpPlannedOrderRepository,
            ProductComponentRepository componentRepository,
            ObjectMapper objectMapper) {
        this.productRepository = productRepository;
        this.stockSnapshotRepository = stockSnapshotRepository;
        this.productionOrderRepository = productionOrderRepository;
        this.mrpPlannedOrderRepository = mrpPlannedOrderRepository;
        this.componentRepository = componentRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Container interno — separado dos DTOs públicos para permitir persistência no RunMrpUseCase
     * sem expor entidades JPA ao controller (ADR-043 Decisão 2).
     */
    public record CalculationOutput(
            MrpRun run,
            List<MrpPlannedOrder> suggestions,
            List<PurchaseNeed> purchaseNeeds,
            List<String> messages
    ) {
        public record PurchaseNeed(String productCode, String productName, Integer quantity, String unit) {}
    }

    public CalculationOutput calculate(boolean isDryRun, String username) {
        LocalDate today = LocalDate.now();
        List<Product> activeFinished = productRepository.findAll().stream()
                .filter(p -> p.getType() == ProductType.FINISHED && p.isActive())
                .toList();

        List<MrpPlannedOrder> suggestions = new ArrayList<>();
        List<CalculationOutput.PurchaseNeed> purchaseNeeds = new ArrayList<>();
        List<String> messages = new ArrayList<>();
        int alreadyOk = 0;

        // Snapshot do estoque mais recente (todos os produtos de uma vez)
        Map<UUID, Integer> stockByProduct = stockSnapshotRepository
                .findLatestPerProduct().stream()
                .collect(Collectors.toMap(
                        s -> s.getProduct().getId(),
                        s -> s.getQty() != null ? s.getQty() : 0,
                        (a, b) -> a));

        // ADR-044 Decisão 3 — BOM pré-carregado como Map antes do loop (evita N+1)
        Map<String, List<ProductComponent>> bomByParent = componentRepository.findAllActive()
                .stream()
                .collect(Collectors.groupingBy(c -> c.getParentProduct().getDynamicsCode()));

        for (Product product : activeFinished) {
            if (product.getMinStockQty() == null) {
                messages.add("Produto %s sem minStockQty definido — ignorado no MRP.".formatted(product.getDynamicsCode()));
                continue;
            }

            int stock = stockByProduct.getOrDefault(product.getId(), 0);

            // Open orders: OPs Dynamics abertas
            int openDynamics = productionOrderRepository.sumOpenOrdersQtyByProduct(product.getId());

            // Open orders: sugestões MRP ativas (SUGGESTED + ACCEPTED) — ADR-030 Decisão 3
            int openMrp = mrpPlannedOrderRepository.sumActiveSuggestedQtyByProduct(product.getId());

            int openOrdersQty = openDynamics + openMrp;
            int netNeed = Math.max(0, product.getMinStockQty() - stock - openOrdersQty);

            if (netNeed == 0) {
                alreadyOk++;
                continue;
            }

            int batchSize = product.getBatchSize() != null && product.getBatchSize() > 0
                    ? product.getBatchSize() : 1;
            int suggestedQty = (int) (Math.ceil((double) netNeed / batchSize) * batchSize);

            int leadDays = product.getLeadTimeDays() != null ? product.getLeadTimeDays() : 7;
            LocalDate dueDate = today.plusDays(leadDays);

            MrpPlannedOrder suggestion = new MrpPlannedOrder();
            suggestion.setProduct(product);
            suggestion.setFamily(product.getFamily());
            suggestion.setSuggestedQty(suggestedQty);
            suggestion.setSuggestedStartDate(today);
            suggestion.setSuggestedDueDate(dueDate);
            suggestion.setStatus(MrpOrderStatus.SUGGESTED);

            suggestions.add(suggestion);

            // ADR-044 Decisão 3 — BOM explosion nível 1
            purchaseNeeds.addAll(explodePurchaseNeeds(product, suggestedQty, bomByParent));
        }

        // Serializa messages e purchaseNeeds para armazenamento no MrpRun
        String messagesJson = toJson(messages);
        String purchaseNeedsJson = toJson(purchaseNeeds);

        MrpRun run = new MrpRun();
        run.setRunAt(LocalDateTime.now());
        run.setRunBy(username);
        run.setDryRun(isDryRun);
        run.setStockSnapshotDate(today);
        run.setOrdersSnapshotDate(today);
        run.setProductsAnalyzed(activeFinished.size());
        run.setSuggestionsGenerated(suggestions.size());
        run.setAlreadyOk(alreadyOk);
        run.setMessagesJson(messagesJson);
        run.setPurchaseNeedsJson(purchaseNeedsJson);

        return new CalculationOutput(run, suggestions, purchaseNeeds, messages);
    }

    /**
     * ADR-044 Decisão 3 / ADR-045 Decisão 2 — explode BOM até nível 2 para o produto dado.
     * Nível 1 RAW_MATERIAL → purchaseNeeds com quantidade real.
     * Nível 1 INTERMEDIATE → busca nível 2 no mesmo bomByParent pré-carregado (sem queries extra).
     * Nível 2 RAW_MATERIAL → purchaseNeeds com quantidade ajustada (qty1 × qty2 × suggestedQty).
     * Nível 3+ → ignorado, log.warn emitido.
     * Produto sem BOM → lista vazia (comportamento legado).
     */
    private List<CalculationOutput.PurchaseNeed> explodePurchaseNeeds(
            Product product, int suggestedQty,
            Map<String, List<ProductComponent>> bomByParent) {

        List<ProductComponent> level1 =
                bomByParent.getOrDefault(product.getDynamicsCode(), List.of());

        if (level1.isEmpty()) {
            return List.of();
        }

        List<CalculationOutput.PurchaseNeed> needs = new ArrayList<>();

        for (ProductComponent comp : level1) {
            ProductType compType = comp.getComponentProduct().getType();

            if (compType == ProductType.RAW_MATERIAL) {
                // Nível 1 direto → purchaseNeed
                needs.add(new CalculationOutput.PurchaseNeed(
                        comp.getComponentProduct().getDynamicsCode(),
                        comp.getComponentProduct().getName(),
                        (int) Math.ceil(suggestedQty * comp.getQuantity()),
                        comp.getUnit() != null ? comp.getUnit() : "UN"
                ));

            } else if (compType == ProductType.INTERMEDIATE) {
                // Nível 2: busca BOM do INTERMEDIATE no mesmo mapa pré-carregado (O(0) extra)
                List<ProductComponent> level2 = bomByParent
                        .getOrDefault(comp.getComponentProduct().getDynamicsCode(), List.of());

                for (ProductComponent sub : level2) {
                    if (sub.getComponentProduct().getType() == ProductType.RAW_MATERIAL) {
                        double combinedQty = suggestedQty * comp.getQuantity() * sub.getQuantity();
                        needs.add(new CalculationOutput.PurchaseNeed(
                                sub.getComponentProduct().getDynamicsCode(),
                                sub.getComponentProduct().getName(),
                                (int) Math.ceil(combinedQty),
                                sub.getUnit() != null ? sub.getUnit() : "UN"
                        ));
                    } else {
                        // Nível 3+ ignorado no MVP (ADR-045 ⚠️)
                        log.warn("BOM profundidade > 2 detectado para {}: componente {} ignorado",
                                product.getDynamicsCode(),
                                sub.getComponentProduct().getDynamicsCode());
                    }
                }
            }
        }
        return needs;
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    /** Container de purchase needs para serialização JSON */
    record PurchaseNeedJson(String productCode, String productName, Integer quantity, String unit) {}
}
