package com.industrialhub.backend.production.application.dto;

import java.util.List;

/** ADR-043 Decisão 2 — resposta unificada para dry-run e run */
public record MrpRunResult(
        MrpRunResponse run,
        List<MrpPlannedOrderResponse> suggestions,
        List<PurchaseNeedResponse> purchaseNeeds,
        List<String> messages,
        boolean isDryRun
) {}
