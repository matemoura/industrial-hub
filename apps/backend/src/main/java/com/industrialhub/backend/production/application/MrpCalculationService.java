package com.industrialhub.backend.production.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.industrialhub.backend.production.domain.*;
import com.industrialhub.backend.production.infrastructure.*;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * ADR-043 Decisão 2 — helper stateless compartilhado por RunMrpUseCase e DryRunMrpUseCase.
 * Sem @Transactional própria: dry-run apenas lê (repos têm readOnly por default),
 * run é envolvido pelo @Transactional do RunMrpUseCase.
 */
@Service
public class MrpCalculationService {

    private final ProductRepository productRepository;
    private final StockSnapshotRepository stockSnapshotRepository;
    private final ProductionOrderRepository productionOrderRepository;
    private final MrpPlannedOrderRepository mrpPlannedOrderRepository;
    private final ObjectMapper objectMapper;

    public MrpCalculationService(
            ProductRepository productRepository,
            StockSnapshotRepository stockSnapshotRepository,
            ProductionOrderRepository productionOrderRepository,
            MrpPlannedOrderRepository mrpPlannedOrderRepository,
            ObjectMapper objectMapper) {
        this.productRepository = productRepository;
        this.stockSnapshotRepository = stockSnapshotRepository;
        this.productionOrderRepository = productionOrderRepository;
        this.mrpPlannedOrderRepository = mrpPlannedOrderRepository;
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
        Map<java.util.UUID, Integer> stockByProduct = stockSnapshotRepository
                .findLatestPerProduct().stream()
                .collect(Collectors.toMap(
                        s -> s.getProduct().getId(),
                        s -> s.getQty() != null ? s.getQty() : 0,
                        (a, b) -> a));

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

            // RAW_MATERIAL via BOM — fora de scope neste sprint (BOM não importado)
            // placeholder: nenhum purchaseNeed gerado por enquanto
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
