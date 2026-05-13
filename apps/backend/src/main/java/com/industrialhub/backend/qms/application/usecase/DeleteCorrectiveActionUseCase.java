package com.industrialhub.backend.qms.application.usecase;

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
public class DeleteCorrectiveActionUseCase {

    private final NonConformanceRepository ncRepository;
    private final CorrectiveActionRepository actionRepository;

    public DeleteCorrectiveActionUseCase(NonConformanceRepository ncRepository,
                                          CorrectiveActionRepository actionRepository) {
        this.ncRepository = ncRepository;
        this.actionRepository = actionRepository;
    }

    @Transactional
    public void execute(UUID ncId, UUID actionId) {
        if (!ncRepository.existsById(ncId)) {
            throw new NcNotFoundException(ncId);
        }

        CorrectiveAction action = actionRepository.findById(actionId)
                .orElseThrow(() -> new ActionNotFoundException(actionId));

        if (action.getStatus() != ActionStatus.PENDING) {
            throw new ActionNotAllowedException("Apenas ações com status PENDING podem ser excluídas");
        }

        actionRepository.delete(action);
    }
}
