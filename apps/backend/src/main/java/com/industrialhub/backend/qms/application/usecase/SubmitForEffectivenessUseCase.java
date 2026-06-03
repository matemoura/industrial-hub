package com.industrialhub.backend.qms.application.usecase;

import com.industrialhub.backend.qms.application.dto.ActionResponse;
import com.industrialhub.backend.qms.domain.ActionNotAllowedException;
import com.industrialhub.backend.qms.domain.ActionNotFoundException;
import com.industrialhub.backend.qms.domain.ActionStatus;
import com.industrialhub.backend.qms.domain.CorrectiveAction;
import com.industrialhub.backend.qms.domain.NcNotFoundException;
import com.industrialhub.backend.qms.infrastructure.CorrectiveActionRepository;
import com.industrialhub.backend.qms.infrastructure.NonConformanceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class SubmitForEffectivenessUseCase {

    private final NonConformanceRepository ncRepository;
    private final CorrectiveActionRepository actionRepository;

    public SubmitForEffectivenessUseCase(NonConformanceRepository ncRepository,
                                          CorrectiveActionRepository actionRepository) {
        this.ncRepository = ncRepository;
        this.actionRepository = actionRepository;
    }

    @Transactional
    public ActionResponse execute(UUID ncId, UUID actionId, String username) {
        if (!ncRepository.existsById(ncId)) {
            throw new NcNotFoundException(ncId);
        }

        CorrectiveAction action = actionRepository.findById(actionId)
                .orElseThrow(() -> new ActionNotFoundException(actionId));

        if (!action.getNonConformance().getId().equals(ncId)) {
            throw new ActionNotFoundException(actionId);
        }

        if (action.getStatus() != ActionStatus.PENDING) {
            throw new ActionNotAllowedException("Apenas ações PENDING podem ser enviadas para verificação de eficácia");
        }

        if (action.getRootCauseConfirmed() == null || action.getRootCauseConfirmed().isBlank()) {
            throw new ActionNotAllowedException("Causa raiz confirmada é obrigatória antes da verificação de eficácia");
        }

        action.setStatus(ActionStatus.PENDING_EFFECTIVENESS);
        actionRepository.save(action);

        return ActionResponse.from(action);
    }
}
