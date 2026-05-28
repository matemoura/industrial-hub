package com.industrialhub.backend.production.application.usecase;

import com.industrialhub.backend.production.application.MrpCalculationService;
import com.industrialhub.backend.production.application.dto.MrpPlannedOrderResponse;
import com.industrialhub.backend.production.application.dto.MrpRunResult;
import com.industrialhub.backend.production.application.dto.PurchaseNeedResponse;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * ADR-043 Decisão 2 — dry-run sem @Transactional: leitura pura, nada é persistido.
 */
@Service
public class DryRunMrpUseCase {

    private final MrpCalculationService calculationService;

    public DryRunMrpUseCase(MrpCalculationService calculationService) {
        this.calculationService = calculationService;
    }

    public MrpRunResult execute(String username) {
        MrpCalculationService.CalculationOutput output = calculationService.calculate(true, username);

        List<MrpPlannedOrderResponse> suggestionResponses = output.suggestions().stream()
                .map(MrpPlannedOrderResponse::from)
                .toList();

        List<PurchaseNeedResponse> purchaseNeedResponses = output.purchaseNeeds().stream()
                .map(pn -> new PurchaseNeedResponse(pn.productCode(), pn.productName(), pn.quantity(), pn.unit()))
                .toList();

        return new MrpRunResult(
                com.industrialhub.backend.production.application.dto.MrpRunResponse.from(output.run()),
                suggestionResponses,
                purchaseNeedResponses,
                output.messages(),
                true
        );
    }
}
