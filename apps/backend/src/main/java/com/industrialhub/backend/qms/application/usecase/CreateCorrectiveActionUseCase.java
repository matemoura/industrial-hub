package com.industrialhub.backend.qms.application.usecase;

import com.industrialhub.backend.common.application.AuditService;
import com.industrialhub.backend.common.domain.AuditAction;
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

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

@Service
public class CreateCorrectiveActionUseCase {

    private final NonConformanceRepository ncRepository;
    private final CorrectiveActionRepository actionRepository;
    private final AuditService auditService;

    public CreateCorrectiveActionUseCase(NonConformanceRepository ncRepository,
                                         CorrectiveActionRepository actionRepository,
                                         AuditService auditService) {
        this.ncRepository = ncRepository;
        this.actionRepository = actionRepository;
        this.auditService = auditService;
    }

    @Transactional
    public ActionResponse execute(UUID ncId, CreateActionRequest request, String username) {
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
                .createdAt(LocalDate.now())
                .status(ActionStatus.PENDING)
                .build();

        ActionResponse response = ActionResponse.from(actionRepository.save(action));

        auditService.log(username, AuditAction.ACTION_CREATED, "CorrectiveAction",
                response.id(), Map.of("ncId", ncId.toString()));

        return response;
    }
}
