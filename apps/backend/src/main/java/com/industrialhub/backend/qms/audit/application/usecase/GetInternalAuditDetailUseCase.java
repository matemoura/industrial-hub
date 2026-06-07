package com.industrialhub.backend.qms.audit.application.usecase;

import com.industrialhub.backend.qms.audit.application.dto.AuditChecklistItemResponse;
import com.industrialhub.backend.qms.audit.application.dto.AuditFindingResponse;
import com.industrialhub.backend.qms.audit.application.dto.InternalAuditDetailResponse;
import com.industrialhub.backend.qms.audit.domain.InternalAuditNotFoundException;
import com.industrialhub.backend.qms.audit.infrastructure.AuditChecklistItemRepository;
import com.industrialhub.backend.qms.audit.infrastructure.AuditFindingRepository;
import com.industrialhub.backend.qms.audit.infrastructure.InternalAuditRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class GetInternalAuditDetailUseCase {

    private final InternalAuditRepository auditRepository;
    private final AuditChecklistItemRepository checklistRepository;
    private final AuditFindingRepository findingRepository;

    public GetInternalAuditDetailUseCase(InternalAuditRepository auditRepository,
                                          AuditChecklistItemRepository checklistRepository,
                                          AuditFindingRepository findingRepository) {
        this.auditRepository = auditRepository;
        this.checklistRepository = checklistRepository;
        this.findingRepository = findingRepository;
    }

    @Transactional(readOnly = true)
    public InternalAuditDetailResponse execute(UUID id) {
        var audit = auditRepository.findById(id)
            .orElseThrow(() -> new InternalAuditNotFoundException(id));

        List<AuditChecklistItemResponse> items = checklistRepository
            .findByAuditIdOrderByItemOrder(id)
            .stream()
            .map(AuditChecklistItemResponse::from)
            .toList();

        List<AuditFindingResponse> findings = findingRepository
            .findByAuditId(id)
            .stream()
            .map(AuditFindingResponse::from)
            .toList();

        return InternalAuditDetailResponse.from(audit, items, findings);
    }
}
