package com.industrialhub.backend.qms.audit.application.usecase;

import com.industrialhub.backend.qms.audit.application.dto.InternalAuditResponse;
import com.industrialhub.backend.qms.audit.domain.AuditStatus;
import com.industrialhub.backend.qms.audit.domain.AuditType;
import com.industrialhub.backend.qms.audit.infrastructure.AuditChecklistItemRepository;
import com.industrialhub.backend.qms.audit.infrastructure.AuditFindingRepository;
import com.industrialhub.backend.qms.audit.infrastructure.InternalAuditRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
public class GetInternalAuditsUseCase {

    private final InternalAuditRepository auditRepository;
    private final AuditChecklistItemRepository checklistRepository;
    private final AuditFindingRepository findingRepository;

    public GetInternalAuditsUseCase(InternalAuditRepository auditRepository,
                                     AuditChecklistItemRepository checklistRepository,
                                     AuditFindingRepository findingRepository) {
        this.auditRepository = auditRepository;
        this.checklistRepository = checklistRepository;
        this.findingRepository = findingRepository;
    }

    @Transactional(readOnly = true)
    public Page<InternalAuditResponse> execute(AuditStatus status, AuditType auditType,
                                                String leadAuditor, LocalDate from, LocalDate to,
                                                Pageable pageable) {
        return auditRepository.findByFilters(status, auditType, leadAuditor, from, to, pageable)
            .map(a -> InternalAuditResponse.from(
                a,
                checklistRepository.countByAuditId(a.getId()),
                findingRepository.countByAuditId(a.getId()),
                checklistRepository.countByAuditIdAndResponse(a.getId(),
                    com.industrialhub.backend.qms.audit.domain.ChecklistResponse.NON_CONFORMING)
            ));
    }
}
