package com.industrialhub.backend.qms.audit.application.usecase;

import com.industrialhub.backend.common.application.AuditService;
import com.industrialhub.backend.common.domain.AuditAction;
import com.industrialhub.backend.qms.audit.application.dto.InternalAuditResponse;
import com.industrialhub.backend.qms.audit.application.dto.UpdateAuditStatusRequest;
import com.industrialhub.backend.qms.audit.domain.AuditStatus;
import com.industrialhub.backend.qms.audit.domain.InternalAudit;
import com.industrialhub.backend.qms.audit.domain.InternalAuditNotFoundException;
import com.industrialhub.backend.qms.audit.domain.InvalidAuditStatusTransitionException;
import com.industrialhub.backend.qms.audit.infrastructure.AuditChecklistItemRepository;
import com.industrialhub.backend.qms.audit.infrastructure.AuditFindingRepository;
import com.industrialhub.backend.qms.audit.infrastructure.InternalAuditRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class TransitionAuditStatusUseCase {

    private final InternalAuditRepository auditRepository;
    private final AuditChecklistItemRepository checklistRepository;
    private final AuditFindingRepository findingRepository;
    private final AuditService auditService;

    public TransitionAuditStatusUseCase(InternalAuditRepository auditRepository,
                                         AuditChecklistItemRepository checklistRepository,
                                         AuditFindingRepository findingRepository,
                                         AuditService auditService) {
        this.auditRepository = auditRepository;
        this.checklistRepository = checklistRepository;
        this.findingRepository = findingRepository;
        this.auditService = auditService;
    }

    @Transactional
    public InternalAuditResponse execute(UUID id, UpdateAuditStatusRequest req, String principal) {
        InternalAudit audit = auditRepository.findById(id)
            .orElseThrow(() -> new InternalAuditNotFoundException(id));

        AuditStatus current = audit.getStatus();
        AuditStatus target = req.status();

        validateTransition(current, target, req);

        audit.setStatus(target);
        if (target == AuditStatus.COMPLETED) {
            audit.setCompletedDate(req.completedDate());
        }

        InternalAudit saved = auditRepository.save(audit);
        auditService.log(principal, AuditAction.INTERNAL_AUDIT_STATUS_CHANGED, "InternalAudit",
            saved.getId(), java.util.Map.of("from", current.name(), "to", target.name()));

        long checklistCount = checklistRepository.countByAuditId(id);
        long findingCount = findingRepository.countByAuditId(id);
        long ncCount = checklistRepository.countByAuditIdAndResponse(id,
            com.industrialhub.backend.qms.audit.domain.ChecklistResponse.NON_CONFORMING);
        return InternalAuditResponse.from(saved, checklistCount, findingCount, ncCount);
    }

    private void validateTransition(AuditStatus current, AuditStatus target,
                                     UpdateAuditStatusRequest req) {
        switch (current) {
            case PLANNED -> {
                if (target != AuditStatus.IN_PROGRESS && target != AuditStatus.CANCELLED) {
                    throw new InvalidAuditStatusTransitionException(current, target);
                }
            }
            case IN_PROGRESS -> {
                if (target == AuditStatus.CANCELLED) {
                    throw new InvalidAuditStatusTransitionException(current, target);
                }
                if (target == AuditStatus.COMPLETED && req.completedDate() == null) {
                    throw new InvalidAuditStatusTransitionException(
                        "Conclusão requer data de completude (completedDate)");
                }
                if (target != AuditStatus.COMPLETED) {
                    throw new InvalidAuditStatusTransitionException(current, target);
                }
            }
            default -> throw new InvalidAuditStatusTransitionException(current, target);
        }
    }
}
