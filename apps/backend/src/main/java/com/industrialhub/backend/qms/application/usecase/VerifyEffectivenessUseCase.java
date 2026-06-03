package com.industrialhub.backend.qms.application.usecase;

import com.industrialhub.backend.qms.application.dto.ActionResponse;
import com.industrialhub.backend.qms.application.dto.VerifyEffectivenessRequest;
import com.industrialhub.backend.qms.domain.ActionNotAllowedException;
import com.industrialhub.backend.qms.domain.ActionNotFoundException;
import com.industrialhub.backend.qms.domain.ActionStatus;
import com.industrialhub.backend.qms.domain.CorrectiveAction;
import com.industrialhub.backend.qms.domain.NcNotFoundException;
import com.industrialhub.backend.qms.domain.NcStatus;
import com.industrialhub.backend.qms.domain.NonConformance;
import com.industrialhub.backend.qms.infrastructure.CorrectiveActionRepository;
import com.industrialhub.backend.qms.infrastructure.NonConformanceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class VerifyEffectivenessUseCase {

    private final NonConformanceRepository ncRepository;
    private final CorrectiveActionRepository actionRepository;

    public VerifyEffectivenessUseCase(NonConformanceRepository ncRepository,
                                       CorrectiveActionRepository actionRepository) {
        this.ncRepository = ncRepository;
        this.actionRepository = actionRepository;
    }

    @Transactional
    public ActionResponse execute(UUID ncId, UUID actionId, VerifyEffectivenessRequest req, String username) {
        NonConformance nc = ncRepository.findById(ncId)
                .orElseThrow(() -> new NcNotFoundException(ncId));

        // SEC-139: PESSIMISTIC_WRITE lock prevents TOCTOU race condition on auto-close NC.
        // Two concurrent verify-effectiveness calls on the same action would both read
        // hasOpen=false and both close the NC; the lock serializes them at DB level.
        CorrectiveAction action = actionRepository.findByIdForUpdate(actionId)
                .orElseThrow(() -> new ActionNotFoundException(actionId));

        if (!action.getNonConformance().getId().equals(ncId)) {
            throw new ActionNotFoundException(actionId);
        }

        if (action.getStatus() != ActionStatus.PENDING_EFFECTIVENESS) {
            throw new ActionNotAllowedException("Apenas ações PENDING_EFFECTIVENESS podem ser verificadas");
        }

        action.setStatus(ActionStatus.DONE);
        action.setEffectivenessResult(req.effectivenessResult());
        action.setEffectivenessCheckedBy(req.effectivenessCheckedBy());
        action.setCompletedAt(LocalDateTime.now());
        action.setCompletedBy(username);
        actionRepository.save(action);

        boolean hasOpen = actionRepository.existsByNonConformanceIdAndStatusIn(
                ncId, List.of(ActionStatus.PENDING, ActionStatus.PENDING_EFFECTIVENESS));
        if (!hasOpen && nc.getStatus() == NcStatus.IN_ANALYSIS) {
            nc.setStatus(NcStatus.CLOSED);
            nc.setClosedAt(LocalDateTime.now());
            nc.setClosedBy(username);
            ncRepository.save(nc);
        }

        return ActionResponse.from(action);
    }
}
