package com.industrialhub.backend.qms.audit.application.usecase;

import com.industrialhub.backend.qms.audit.application.dto.AuditChecklistItemResponse;
import com.industrialhub.backend.qms.audit.application.dto.UpdateAuditChecklistItemRequest;
import com.industrialhub.backend.qms.audit.domain.AuditChecklistItem;
import com.industrialhub.backend.qms.audit.domain.AuditChecklistItemNotFoundException;
import com.industrialhub.backend.qms.audit.infrastructure.AuditChecklistItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class UpdateAuditChecklistItemUseCase {

    private final AuditChecklistItemRepository checklistRepository;

    public UpdateAuditChecklistItemUseCase(AuditChecklistItemRepository checklistRepository) {
        this.checklistRepository = checklistRepository;
    }

    @Transactional
    public AuditChecklistItemResponse execute(UUID auditId, UUID itemId,
                                               UpdateAuditChecklistItemRequest req) {
        AuditChecklistItem item = checklistRepository.findById(itemId)
            .orElseThrow(() -> new AuditChecklistItemNotFoundException(itemId));

        if (!item.getAudit().getId().equals(auditId)) {
            throw new AuditChecklistItemNotFoundException(itemId);
        }

        item.setResponse(req.response());
        item.setEvidence(req.evidence());

        return AuditChecklistItemResponse.from(checklistRepository.save(item));
    }
}
