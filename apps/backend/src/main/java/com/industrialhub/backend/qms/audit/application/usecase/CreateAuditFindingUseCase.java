package com.industrialhub.backend.qms.audit.application.usecase;

import com.industrialhub.backend.common.application.AuditService;
import com.industrialhub.backend.common.domain.AuditAction;
import com.industrialhub.backend.qms.audit.application.dto.AuditFindingResponse;
import com.industrialhub.backend.qms.audit.application.dto.CreateAuditFindingRequest;
import com.industrialhub.backend.qms.audit.domain.AuditChecklistItem;
import com.industrialhub.backend.qms.audit.domain.AuditChecklistItemNotFoundException;
import com.industrialhub.backend.qms.audit.domain.AuditFinding;
import com.industrialhub.backend.qms.audit.domain.AuditStatus;
import com.industrialhub.backend.qms.audit.domain.InternalAudit;
import com.industrialhub.backend.qms.audit.domain.InternalAuditNotFoundException;
import com.industrialhub.backend.qms.audit.domain.InvalidAuditStatusTransitionException;
import com.industrialhub.backend.qms.audit.infrastructure.AuditChecklistItemRepository;
import com.industrialhub.backend.qms.audit.infrastructure.AuditFindingRepository;
import com.industrialhub.backend.qms.audit.infrastructure.InternalAuditRepository;
import com.industrialhub.backend.qms.domain.NcNotFoundException;
import com.industrialhub.backend.qms.infrastructure.NonConformanceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class CreateAuditFindingUseCase {

    private final InternalAuditRepository auditRepository;
    private final AuditFindingRepository findingRepository;
    private final AuditChecklistItemRepository checklistRepository;
    private final NonConformanceRepository ncRepository;
    private final AuditService auditService;

    public CreateAuditFindingUseCase(InternalAuditRepository auditRepository,
                                      AuditFindingRepository findingRepository,
                                      AuditChecklistItemRepository checklistRepository,
                                      NonConformanceRepository ncRepository,
                                      AuditService auditService) {
        this.auditRepository = auditRepository;
        this.findingRepository = findingRepository;
        this.checklistRepository = checklistRepository;
        this.ncRepository = ncRepository;
        this.auditService = auditService;
    }

    @Transactional
    public AuditFindingResponse execute(UUID auditId, CreateAuditFindingRequest req, String principal) {
        InternalAudit audit = auditRepository.findById(auditId)
            .orElseThrow(() -> new InternalAuditNotFoundException(auditId));

        if (audit.getStatus() == AuditStatus.COMPLETED) {
            throw new InvalidAuditStatusTransitionException(
                "Não é possível adicionar achados em auditoria COMPLETED");
        }

        AuditChecklistItem checklistItem = null;
        if (req.checklistItemId() != null) {
            checklistItem = checklistRepository.findById(req.checklistItemId())
                .orElseThrow(() -> new AuditChecklistItemNotFoundException(req.checklistItemId()));
        }

        if (req.linkedNcId() != null && !ncRepository.existsById(req.linkedNcId())) {
            throw new NcNotFoundException(req.linkedNcId());
        }

        AuditFinding finding = AuditFinding.builder()
            .audit(audit)
            .checklistItem(checklistItem)
            .type(req.type())
            .description(req.description())
            .isoClause(req.isoClause())
            .severity(req.severity())
            .linkedNcId(req.linkedNcId())
            .linkedCapaId(req.linkedCapaId())
            .createdBy(principal)
            .build();

        AuditFinding saved = findingRepository.save(finding);
        auditService.log(principal, AuditAction.AUDIT_FINDING_CREATED, "AuditFinding", saved.getId(), null);
        return AuditFindingResponse.from(saved);
    }
}
