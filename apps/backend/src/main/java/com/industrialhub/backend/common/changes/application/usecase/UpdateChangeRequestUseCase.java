package com.industrialhub.backend.common.changes.application.usecase;

import com.industrialhub.backend.common.changes.application.dto.ChangeRequestResponse;
import com.industrialhub.backend.common.changes.application.dto.UpdateChangeRequestRequest;
import com.industrialhub.backend.common.changes.domain.ChangeRequest;
import com.industrialhub.backend.common.changes.domain.ChangeRequestForbiddenException;
import com.industrialhub.backend.common.changes.domain.ChangeRequestNotFoundException;
import com.industrialhub.backend.common.changes.domain.ChangeStatus;
import com.industrialhub.backend.common.changes.domain.InvalidChangeStatusTransitionException;
import com.industrialhub.backend.common.changes.infrastructure.ChangeRequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class UpdateChangeRequestUseCase {

    private final ChangeRequestRepository changeRequestRepository;

    public UpdateChangeRequestUseCase(ChangeRequestRepository changeRequestRepository) {
        this.changeRequestRepository = changeRequestRepository;
    }

    @Transactional
    public ChangeRequestResponse execute(UUID id, UpdateChangeRequestRequest req, String principal) {
        ChangeRequest cr = changeRequestRepository.findById(id)
            .orElseThrow(() -> new ChangeRequestNotFoundException(id));

        if (!cr.getRequestedBy().equals(principal)) {
            throw new ChangeRequestForbiddenException("Only the requester can update this change request");
        }

        if (cr.getStatus() != ChangeStatus.DRAFT) {
            throw new InvalidChangeStatusTransitionException(
                "Change request can only be updated in DRAFT status, current: " + cr.getStatus());
        }

        cr.setTitle(req.title());
        cr.setDescription(req.description());
        cr.setChangeType(req.changeType());
        cr.setJustification(req.justification());

        return ChangeRequestResponse.from(changeRequestRepository.save(cr));
    }
}
