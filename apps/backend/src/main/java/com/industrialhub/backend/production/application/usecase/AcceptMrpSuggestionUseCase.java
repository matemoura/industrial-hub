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

@Service
public class AcceptMrpSuggestionUseCase {

    private final MrpPlannedOrderRepository mrpPlannedOrderRepository;
    private final AuditService auditService;

    public AcceptMrpSuggestionUseCase(
            MrpPlannedOrderRepository mrpPlannedOrderRepository,
            AuditService auditService) {
        this.mrpPlannedOrderRepository = mrpPlannedOrderRepository;
        this.auditService = auditService;
    }

    @Transactional
    public MrpPlannedOrderResponse execute(UUID suggestionId, Integer adjustedQty, String username) {
        MrpPlannedOrder suggestion = mrpPlannedOrderRepository.findById(suggestionId)
                .orElseThrow(() -> new MrpSuggestionNotFoundException(suggestionId));

        if (suggestion.getStatus() != MrpOrderStatus.SUGGESTED) {
            throw new InvalidMrpSuggestionStatusException(
                    "Sugestão não está com status SUGGESTED. Status atual: " + suggestion.getStatus());
        }

        suggestion.setStatus(MrpOrderStatus.ACCEPTED);
        suggestion.setReviewedBy(username);
        suggestion.setReviewedAt(LocalDateTime.now());
        if (adjustedQty != null && adjustedQty > 0) {
            suggestion.setAdjustedQty(adjustedQty);
        }

        MrpPlannedOrder saved = mrpPlannedOrderRepository.save(suggestion);

        auditService.log(username, AuditAction.MRP_SUGGESTION_ACCEPTED,
                "MrpPlannedOrder", suggestionId.toString(),
                Map.of("productCode", suggestion.getProduct().getDynamicsCode(),
                       "adjustedQty", adjustedQty != null ? String.valueOf(adjustedQty) : "none"));

        return MrpPlannedOrderResponse.from(saved);
    }
}
