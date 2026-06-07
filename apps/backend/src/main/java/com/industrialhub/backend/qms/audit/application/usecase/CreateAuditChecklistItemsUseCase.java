package com.industrialhub.backend.qms.audit.application.usecase;

import com.industrialhub.backend.qms.audit.application.dto.AuditChecklistItemResponse;
import com.industrialhub.backend.qms.audit.application.dto.CreateAuditChecklistItemRequest;
import com.industrialhub.backend.qms.audit.domain.AuditChecklistItem;
import com.industrialhub.backend.qms.audit.domain.AuditStatus;
import com.industrialhub.backend.qms.audit.domain.InternalAudit;
import com.industrialhub.backend.qms.audit.domain.InternalAuditNotFoundException;
import com.industrialhub.backend.qms.audit.domain.InvalidAuditStatusTransitionException;
import com.industrialhub.backend.qms.audit.infrastructure.AuditChecklistItemRepository;
import com.industrialhub.backend.qms.audit.infrastructure.InternalAuditRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class CreateAuditChecklistItemsUseCase {

    private final InternalAuditRepository auditRepository;
    private final AuditChecklistItemRepository checklistRepository;

    public CreateAuditChecklistItemsUseCase(InternalAuditRepository auditRepository,
                                             AuditChecklistItemRepository checklistRepository) {
        this.auditRepository = auditRepository;
        this.checklistRepository = checklistRepository;
    }

    @Transactional
    public List<AuditChecklistItemResponse> execute(UUID auditId,
                                                     List<CreateAuditChecklistItemRequest> requests) {
        InternalAudit audit = auditRepository.findById(auditId)
            .orElseThrow(() -> new InternalAuditNotFoundException(auditId));

        if (audit.getStatus() != AuditStatus.IN_PROGRESS) {
            throw new InvalidAuditStatusTransitionException(
                "Itens de checklist só podem ser adicionados quando a auditoria está IN_PROGRESS");
        }

        long currentMax = checklistRepository.countByAuditId(auditId);
        AtomicInteger orderCounter = new AtomicInteger((int) currentMax + 1);

        List<AuditChecklistItem> items = requests.stream()
            .map(req -> AuditChecklistItem.builder()
                .audit(audit)
                .process(req.process())
                .isoClause(req.isoClause())
                .question(req.question())
                .itemOrder(orderCounter.getAndIncrement())
                .build())
            .toList();

        return checklistRepository.saveAll(items).stream()
            .map(AuditChecklistItemResponse::from)
            .toList();
    }
}
