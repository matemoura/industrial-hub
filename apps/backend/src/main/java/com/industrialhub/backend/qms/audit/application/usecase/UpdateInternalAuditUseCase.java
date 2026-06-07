package com.industrialhub.backend.qms.audit.application.usecase;

import com.industrialhub.backend.qms.audit.application.dto.InternalAuditResponse;
import com.industrialhub.backend.qms.audit.application.dto.UpdateInternalAuditRequest;
import com.industrialhub.backend.qms.audit.domain.AuditStatus;
import com.industrialhub.backend.qms.audit.domain.InternalAudit;
import com.industrialhub.backend.qms.audit.domain.InternalAuditNotFoundException;
import com.industrialhub.backend.qms.audit.domain.InvalidAuditStatusTransitionException;
import com.industrialhub.backend.qms.audit.infrastructure.AuditChecklistItemRepository;
import com.industrialhub.backend.qms.audit.infrastructure.AuditFindingRepository;
import com.industrialhub.backend.qms.audit.infrastructure.InternalAuditRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.UUID;

@Service
public class UpdateInternalAuditUseCase {

    private final InternalAuditRepository auditRepository;
    private final AuditChecklistItemRepository checklistRepository;
    private final AuditFindingRepository findingRepository;

    public UpdateInternalAuditUseCase(InternalAuditRepository auditRepository,
                                       AuditChecklistItemRepository checklistRepository,
                                       AuditFindingRepository findingRepository) {
        this.auditRepository = auditRepository;
        this.checklistRepository = checklistRepository;
        this.findingRepository = findingRepository;
    }

    @Transactional
    public InternalAuditResponse execute(UUID id, UpdateInternalAuditRequest req, String principal) {
        InternalAudit audit = auditRepository.findById(id)
            .orElseThrow(() -> new InternalAuditNotFoundException(id));

        if (audit.getStatus() != AuditStatus.PLANNED) {
            throw new InvalidAuditStatusTransitionException(
                "Auditoria só pode ser editada no status PLANNED");
        }

        audit.setTitle(req.title());
        audit.setScope(req.scope());
        audit.setAuditType(req.auditType());
        audit.setPlannedDate(req.plannedDate());
        audit.setLeadAuditor(req.leadAuditor());
        if (req.auditees() != null) {
            audit.getAuditees().clear();
            audit.getAuditees().addAll(req.auditees());
        }

        InternalAudit saved = auditRepository.save(audit);
        long checklistCount = checklistRepository.countByAuditId(id);
        long findingCount = findingRepository.countByAuditId(id);
        long ncCount = checklistRepository.countByAuditIdAndResponse(id,
            com.industrialhub.backend.qms.audit.domain.ChecklistResponse.NON_CONFORMING);
        return InternalAuditResponse.from(saved, checklistCount, findingCount, ncCount);
    }
}
