package com.industrialhub.backend.production.application.usecase;

import com.industrialhub.backend.common.application.AuditService;
import com.industrialhub.backend.common.domain.AuditAction;
import com.industrialhub.backend.production.application.dto.MrpPlannedOrderResponse;
import com.industrialhub.backend.production.domain.*;
import com.industrialhub.backend.production.infrastructure.MrpPlannedOrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/** ADR-043 Decisão 7 — apenas ACCEPTED → CONVERTED; outros status → 409 */
@Service
public class ConvertMrpSuggestionUseCase {

    private final MrpPlannedOrderRepository mrpPlannedOrderRepository;
    private final AuditService auditService;

    public ConvertMrpSuggestionUseCase(
            MrpPlannedOrderRepository mrpPlannedOrderRepository,
            AuditService auditService) {
        this.mrpPlannedOrderRepository = mrpPlannedOrderRepository;
        this.auditService = auditService;
    }

    @Transactional
    public MrpPlannedOrderResponse execute(UUID suggestionId, String username) {
        MrpPlannedOrder suggestion = mrpPlannedOrderRepository.findById(suggestionId)
                .orElseThrow(() -> new MrpSuggestionNotFoundException(suggestionId));

        if (suggestion.getStatus() != MrpOrderStatus.ACCEPTED) {
            throw new InvalidMrpSuggestionStatusException(
                    "Sugestão não está aceita. Apenas sugestões ACCEPTED podem ser convertidas. Status atual: "
                            + suggestion.getStatus());
        }

        suggestion.setStatus(MrpOrderStatus.CONVERTED);
        suggestion.setReviewedBy(username);
        suggestion.setReviewedAt(LocalDateTime.now());

        MrpPlannedOrder saved = mrpPlannedOrderRepository.save(suggestion);

        auditService.log(username, AuditAction.MRP_SUGGESTION_CONVERTED,
                "MrpPlannedOrder", suggestionId.toString(),
                Map.of("productCode", suggestion.getProduct().getDynamicsCode()));

        return MrpPlannedOrderResponse.from(saved);
    }
}
