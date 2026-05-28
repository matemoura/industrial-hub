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
public class RejectMrpSuggestionUseCase {

    private final MrpPlannedOrderRepository mrpPlannedOrderRepository;
    private final AuditService auditService;

    public RejectMrpSuggestionUseCase(
            MrpPlannedOrderRepository mrpPlannedOrderRepository,
            AuditService auditService) {
        this.mrpPlannedOrderRepository = mrpPlannedOrderRepository;
        this.auditService = auditService;
    }

    @Transactional
    public MrpPlannedOrderResponse execute(UUID suggestionId, String reason, String username) {
        MrpPlannedOrder suggestion = mrpPlannedOrderRepository.findById(suggestionId)
                .orElseThrow(() -> new MrpSuggestionNotFoundException(suggestionId));

        if (suggestion.getStatus() != MrpOrderStatus.SUGGESTED
                && suggestion.getStatus() != MrpOrderStatus.ACCEPTED) {
            throw new InvalidMrpSuggestionStatusException(
                    "Sugestão não pode ser rejeitada com status: " + suggestion.getStatus());
        }

        suggestion.setStatus(MrpOrderStatus.REJECTED);
        suggestion.setRejectionReason(reason);
        suggestion.setReviewedBy(username);
        suggestion.setReviewedAt(LocalDateTime.now());

        MrpPlannedOrder saved = mrpPlannedOrderRepository.save(suggestion);

        auditService.log(username, AuditAction.MRP_SUGGESTION_REJECTED,
                "MrpPlannedOrder", suggestionId.toString(),
                Map.of("productCode", suggestion.getProduct().getDynamicsCode(), "reason", reason));

        return MrpPlannedOrderResponse.from(saved);
    }
}
