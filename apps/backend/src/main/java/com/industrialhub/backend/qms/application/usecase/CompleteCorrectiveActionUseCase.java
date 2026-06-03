package com.industrialhub.backend.qms.application.usecase;

import com.industrialhub.backend.common.application.AuditService;
import com.industrialhub.backend.common.domain.AuditAction;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class CompleteCorrectiveActionUseCase {

    private final NonConformanceRepository ncRepository;
    private final CorrectiveActionRepository actionRepository;
    private final QmsEmailService emailService;
    private final AuditService auditService;

    public CompleteCorrectiveActionUseCase(NonConformanceRepository ncRepository,
                                            CorrectiveActionRepository actionRepository,
                                            QmsEmailService emailService,
                                            AuditService auditService) {
        this.ncRepository = ncRepository;
        this.actionRepository = actionRepository;
        this.emailService = emailService;
        this.auditService = auditService;
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

        // CAPAS formais (com tipo definido) devem passar por PENDING_EFFECTIVENESS antes de DONE
        if (action.getType() != null && action.getStatus() == ActionStatus.PENDING) {
            throw new ActionNotAllowedException(
                "Ações CAPA devem ser enviadas para verificação de eficácia antes de serem concluídas");
        }

        action.setStatus(ActionStatus.DONE);
        action.setCompletedAt(LocalDateTime.now());
        action.setCompletedBy(username);
        actionRepository.save(action);

        auditService.log(username, AuditAction.ACTION_COMPLETED, "CorrectiveAction",
                actionId, Map.of("ncId", ncId.toString()));

        boolean hasOpen = actionRepository.existsByNonConformanceIdAndStatusIn(
                ncId, List.of(ActionStatus.PENDING, ActionStatus.PENDING_EFFECTIVENESS));
        if (!hasOpen) {
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
