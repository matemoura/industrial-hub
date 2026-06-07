package com.industrialhub.backend.qms.audit.application.usecase;

import com.industrialhub.backend.qms.audit.domain.AuditFindingNotFoundException;
import com.industrialhub.backend.qms.audit.domain.AuditStatus;
import com.industrialhub.backend.qms.audit.domain.InvalidAuditStatusTransitionException;
import com.industrialhub.backend.qms.audit.infrastructure.AuditFindingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class DeleteAuditFindingUseCase {

    private final AuditFindingRepository findingRepository;

    public DeleteAuditFindingUseCase(AuditFindingRepository findingRepository) {
        this.findingRepository = findingRepository;
    }

    @Transactional
    public void execute(UUID auditId, UUID findingId) {
        var finding = findingRepository.findById(findingId)
            .orElseThrow(() -> new AuditFindingNotFoundException(findingId));

        if (!finding.getAudit().getId().equals(auditId)) {
            throw new AuditFindingNotFoundException(findingId);
        }

        if (finding.getAudit().getStatus() == AuditStatus.COMPLETED) {
            throw new InvalidAuditStatusTransitionException(
                "Não é possível remover achados de auditoria COMPLETED");
        }

        findingRepository.delete(finding);
    }
}
