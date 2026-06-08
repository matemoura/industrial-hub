package com.industrialhub.backend.common.changes.application.usecase;

import com.industrialhub.backend.common.application.AuditService;
import com.industrialhub.backend.common.changes.application.dto.ChangeRequestResponse;
import com.industrialhub.backend.common.changes.domain.ChangeRequest;
import com.industrialhub.backend.common.changes.domain.ChangeRequestForbiddenException;
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
public class SubmitChangeRequestUseCase {

    private final ChangeRequestRepository changeRequestRepository;
    private final AuditService auditService;

    public SubmitChangeRequestUseCase(ChangeRequestRepository changeRequestRepository,
                                      AuditService auditService) {
        this.changeRequestRepository = changeRequestRepository;
        this.auditService = auditService;
    }

    @Transactional
    public ChangeRequestResponse execute(UUID id, String principal) {
        ChangeRequest cr = changeRequestRepository.findById(id)
            .orElseThrow(() -> new ChangeRequestNotFoundException(id));

        if (!cr.getRequestedBy().equals(principal)) {
            throw new ChangeRequestForbiddenException("Only the requester can submit this change request");
        }

        if (cr.getStatus() != ChangeStatus.DRAFT) {
            throw new InvalidChangeStatusTransitionException(cr.getStatus(), ChangeStatus.SUBMITTED);
        }

        cr.setStatus(ChangeStatus.SUBMITTED);
        cr.setSubmittedAt(LocalDateTime.now());

        ChangeRequest saved = changeRequestRepository.save(cr);
        auditService.log(principal, AuditAction.CR_SUBMITTED, "ChangeRequest", saved.getId(), null);
        return ChangeRequestResponse.from(saved);
    }
}
