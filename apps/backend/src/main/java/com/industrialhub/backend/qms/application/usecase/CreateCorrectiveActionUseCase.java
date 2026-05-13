package com.industrialhub.backend.qms.application.usecase;

import com.industrialhub.backend.qms.application.dto.ActionResponse;
import com.industrialhub.backend.qms.application.dto.CreateActionRequest;
import com.industrialhub.backend.qms.domain.ActionNotAllowedException;
import com.industrialhub.backend.qms.domain.ActionStatus;
import com.industrialhub.backend.qms.domain.CorrectiveAction;
import com.industrialhub.backend.qms.domain.NcNotFoundException;
import com.industrialhub.backend.qms.domain.NcStatus;
import com.industrialhub.backend.qms.domain.NonConformance;
import com.industrialhub.backend.qms.infrastructure.CorrectiveActionRepository;
import com.industrialhub.backend.qms.infrastructure.NonConformanceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class CreateCorrectiveActionUseCase {

    private final NonConformanceRepository ncRepository;
    private final CorrectiveActionRepository actionRepository;

    public CreateCorrectiveActionUseCase(NonConformanceRepository ncRepository,
                                         CorrectiveActionRepository actionRepository) {
        this.ncRepository = ncRepository;
        this.actionRepository = actionRepository;
    }

    @Transactional
    public ActionResponse execute(UUID ncId, CreateActionRequest request) {
        NonConformance nc = ncRepository.findById(ncId)
                .orElseThrow(() -> new NcNotFoundException(ncId));

        if (nc.getStatus() != NcStatus.IN_ANALYSIS) {
            throw new ActionNotAllowedException(
                "Ações corretivas só podem ser adicionadas a NCs em status IN_ANALYSIS"
            );
        }

        CorrectiveAction action = CorrectiveAction.builder()
                .nonConformance(nc)
                .description(request.description())
                .responsible(request.responsible())
                .dueDate(request.dueDate())
                .status(ActionStatus.PENDING)
                .build();

        return ActionResponse.from(actionRepository.save(action));
    }
}
