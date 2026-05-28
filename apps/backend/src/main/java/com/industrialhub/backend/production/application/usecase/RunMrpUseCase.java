package com.industrialhub.backend.production.application.usecase;

import com.industrialhub.backend.common.application.AuditService;
import com.industrialhub.backend.common.domain.AuditAction;
import com.industrialhub.backend.production.application.MrpCalculationService;
import com.industrialhub.backend.production.application.dto.MrpPlannedOrderResponse;
import com.industrialhub.backend.production.application.dto.MrpRunResult;
import com.industrialhub.backend.production.application.dto.PurchaseNeedResponse;
import com.industrialhub.backend.production.domain.MrpPlannedOrder;
import com.industrialhub.backend.production.domain.MrpRun;
import com.industrialhub.backend.production.infrastructure.MrpPlannedOrderRepository;
import com.industrialhub.backend.production.infrastructure.MrpRunRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class RunMrpUseCase {

    private final MrpCalculationService calculationService;
    private final MrpRunRepository mrpRunRepository;
    private final MrpPlannedOrderRepository mrpPlannedOrderRepository;
    private final AuditService auditService;

    public RunMrpUseCase(
            MrpCalculationService calculationService,
            MrpRunRepository mrpRunRepository,
            MrpPlannedOrderRepository mrpPlannedOrderRepository,
            AuditService auditService) {
        this.calculationService = calculationService;
        this.mrpRunRepository = mrpRunRepository;
        this.mrpPlannedOrderRepository = mrpPlannedOrderRepository;
        this.auditService = auditService;
    }

    @Transactional
    public MrpRunResult execute(String username) {
        // ADR-043 Decisão 3 — invalida sugestões SUGGESTED antes de gerar novas
        mrpPlannedOrderRepository.supersedePendingSuggestions();

        MrpCalculationService.CalculationOutput output = calculationService.calculate(false, username);

        // Persiste MrpRun
        MrpRun savedRun = mrpRunRepository.save(output.run());

        // Associa sugestões ao run e persiste
        List<MrpPlannedOrder> suggestions = output.suggestions();
        suggestions.forEach(s -> s.setMrpRun(savedRun));
        List<MrpPlannedOrder> savedSuggestions = mrpPlannedOrderRepository.saveAll(suggestions);

        auditService.log(username, AuditAction.MRP_RUN_EXECUTED,
                "MrpRun", savedRun.getId().toString(),
                Map.of("suggestionsGenerated", String.valueOf(savedSuggestions.size()),
                       "productsAnalyzed", String.valueOf(output.run().getProductsAnalyzed())));

        List<MrpPlannedOrderResponse> suggestionResponses = savedSuggestions.stream()
                .map(MrpPlannedOrderResponse::from)
                .toList();

        List<PurchaseNeedResponse> purchaseNeedResponses = output.purchaseNeeds().stream()
                .map(pn -> new PurchaseNeedResponse(pn.productCode(), pn.productName(), pn.quantity(), pn.unit()))
                .toList();

        return new MrpRunResult(
                com.industrialhub.backend.production.application.dto.MrpRunResponse.from(savedRun),
                suggestionResponses,
                purchaseNeedResponses,
                output.messages(),
                false
        );
    }
}
