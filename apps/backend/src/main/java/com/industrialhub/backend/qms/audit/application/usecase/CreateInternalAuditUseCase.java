package com.industrialhub.backend.qms.audit.application.usecase;

import com.industrialhub.backend.common.application.AuditService;
import com.industrialhub.backend.common.domain.AuditAction;
import com.industrialhub.backend.qms.audit.application.dto.CreateInternalAuditRequest;
import com.industrialhub.backend.qms.audit.application.dto.InternalAuditResponse;
import com.industrialhub.backend.qms.audit.domain.AuditCodeAlreadyExistsException;
import com.industrialhub.backend.qms.audit.domain.AuditStatus;
import com.industrialhub.backend.qms.audit.domain.InternalAudit;
import com.industrialhub.backend.qms.audit.infrastructure.InternalAuditRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashSet;

@Service
public class CreateInternalAuditUseCase {

    private final InternalAuditRepository auditRepository;
    private final AuditService auditService;

    public CreateInternalAuditUseCase(InternalAuditRepository auditRepository,
                                       AuditService auditService) {
        this.auditRepository = auditRepository;
        this.auditService = auditService;
    }

    @Transactional
    public InternalAuditResponse execute(CreateInternalAuditRequest req, String principal) {
        int year = LocalDate.now().getYear();
        String prefix = "AUD-" + year + "-";
        long count = auditRepository.countByCodeStartingWith(prefix);
        String code = prefix + String.format("%03d", count + 1);

        InternalAudit audit = InternalAudit.builder()
            .code(code)
            .title(req.title())
            .scope(req.scope())
            .auditType(req.auditType())
            .status(AuditStatus.PLANNED)
            .plannedDate(req.plannedDate())
            .leadAuditor(req.leadAuditor())
            .auditees(req.auditees() != null ? new HashSet<>(req.auditees()) : new HashSet<>())
            .createdBy(principal)
            .build();

        try {
            InternalAudit saved = auditRepository.save(audit);
            auditService.log(principal, AuditAction.INTERNAL_AUDIT_CREATED, "InternalAudit", saved.getId(), null);
            return InternalAuditResponse.from(saved, 0, 0, 0);
        } catch (DataIntegrityViolationException e) {
            throw new AuditCodeAlreadyExistsException(code);
        }
    }
}
