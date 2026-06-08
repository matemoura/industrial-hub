package com.industrialhub.backend.common.changes.application.usecase;

import com.industrialhub.backend.common.application.AuditService;
import com.industrialhub.backend.common.changes.application.dto.ApproveChangeRequestRequest;
import com.industrialhub.backend.common.changes.application.dto.ChangeRequestResponse;
import com.industrialhub.backend.common.changes.domain.ChangeRequest;
import com.industrialhub.backend.common.changes.domain.ChangeRequestNotFoundException;
import com.industrialhub.backend.common.changes.domain.ChangeStatus;
import com.industrialhub.backend.common.changes.domain.InvalidChangeStatusTransitionException;
import com.industrialhub.backend.common.changes.infrastructure.ChangeRequestRepository;
import com.industrialhub.backend.common.domain.AuditAction;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class ApproveChangeRequestUseCase {

    private final ChangeRequestRepository changeRequestRepository;
    private final AuditService auditService;

    public ApproveChangeRequestUseCase(ChangeRequestRepository changeRequestRepository,
                                       AuditService auditService) {
        this.changeRequestRepository = changeRequestRepository;
        this.auditService = auditService;
    }

    @Transactional
    public ChangeRequestResponse execute(UUID id, ApproveChangeRequestRequest req, String principal) {
        ChangeRequest cr = changeRequestRepository.findById(id)
            .orElseThrow(() -> new ChangeRequestNotFoundException(id));

        if (cr.getStatus() != ChangeStatus.UNDER_REVIEW) {
            throw new InvalidChangeStatusTransitionException(cr.getStatus(),
                req.approved() ? ChangeStatus.APPROVED : ChangeStatus.REJECTED);
        }

        cr.setApprovedBy(principal);
        cr.setApprovedAt(LocalDateTime.now());

        AuditAction action;
        if (req.approved()) {
            cr.setStatus(ChangeStatus.APPROVED);
            action = AuditAction.CR_APPROVED;
        } else {
            cr.setStatus(ChangeStatus.REJECTED);
            cr.setRejectionReason(req.rejectionReason());
            action = AuditAction.CR_REJECTED;
        }

        ChangeRequest saved = changeRequestRepository.save(cr);
        auditService.log(principal, action, "ChangeRequest", saved.getId(), null);
        return ChangeRequestResponse.from(saved);
    }
}
