package com.industrialhub.backend.qms.application.usecase;

import com.industrialhub.backend.qms.application.dto.CAPASummaryResponse;
import com.industrialhub.backend.qms.application.dto.UpdateCapaDueDateRequest;
import com.industrialhub.backend.qms.domain.ActionNotFoundException;
import com.industrialhub.backend.qms.domain.CorrectiveAction;
import com.industrialhub.backend.qms.infrastructure.CorrectiveActionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Sprint 39 / US-116: atualiza dueDate de uma CAPA.
 */
@Service
@Transactional
public class UpdateCapaDueDateUseCase {

    private final CorrectiveActionRepository correctiveActionRepository;

    public UpdateCapaDueDateUseCase(CorrectiveActionRepository correctiveActionRepository) {
        this.correctiveActionRepository = correctiveActionRepository;
    }

    public CAPASummaryResponse execute(UUID actionId, UpdateCapaDueDateRequest req) {
        CorrectiveAction action = correctiveActionRepository.findById(actionId)
                .orElseThrow(() -> new ActionNotFoundException(actionId));

        action.setDueDate(req.dueDate());

        CorrectiveAction saved = correctiveActionRepository.save(action);
        return new CAPASummaryResponse(
                saved.getId(),
                saved.getNonConformance().getId(),
                saved.getNonConformance().getTitle(),
                saved.getDescription(),
                saved.getType(),
                saved.getStatus(),
                saved.getResponsible(),
                saved.getDueDate(),
                saved.getEffectivenessCheckDate()
        );
    }
}
