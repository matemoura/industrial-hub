package com.industrialhub.backend.qms.application.usecase;

import com.industrialhub.backend.qms.application.dto.ActionResponse;
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
import java.util.UUID;

@Service
public class CompleteCorrectiveActionUseCase {

    private final NonConformanceRepository ncRepository;
    private final CorrectiveActionRepository actionRepository;
    private final QmsEmailService emailService;

    public CompleteCorrectiveActionUseCase(NonConformanceRepository ncRepository,
                                            CorrectiveActionRepository actionRepository,
                                            QmsEmailService emailService) {
        this.ncRepository = ncRepository;
        this.actionRepository = actionRepository;
        this.emailService = emailService;
    }

    @Transactional
    public ActionResponse execute(UUID ncId, UUID actionId, String username) {
        if (!ncRepository.existsById(ncId)) {
            throw new NcNotFoundException(ncId);
        }

        CorrectiveAction action = actionRepository.findById(actionId)
                .orElseThrow(() -> new ActionNotFoundException(actionId));

        if (action.getStatus() == ActionStatus.DONE) {
            throw new ActionNotAllowedException("Ação já foi concluída");
        }

        action.setStatus(ActionStatus.DONE);
        action.setCompletedAt(LocalDateTime.now());
        action.setCompletedBy(username);
        actionRepository.save(action);

        boolean hasPending = actionRepository.existsByNonConformanceIdAndStatus(ncId, ActionStatus.PENDING);
        if (!hasPending) {
            NonConformance nc = ncRepository.findById(ncId)
                    .orElseThrow(() -> new NcNotFoundException(ncId));
            if (nc.getStatus() == NcStatus.IN_ANALYSIS) {
                nc.setStatus(NcStatus.CLOSED);
                nc.setClosedAt(LocalDateTime.now());
                nc.setClosedBy(username);
                ncRepository.save(nc);
                emailService.notifyNcClosed(nc);
            }
        }

        return ActionResponse.from(action);
    }
}
