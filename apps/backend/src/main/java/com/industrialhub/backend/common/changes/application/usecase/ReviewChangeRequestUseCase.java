package com.industrialhub.backend.common.changes.application.usecase;

import com.industrialhub.backend.common.changes.application.dto.ChangeRequestResponse;
import com.industrialhub.backend.common.changes.application.dto.ReviewChangeRequestRequest;
import com.industrialhub.backend.common.changes.domain.ChangeRequest;
import com.industrialhub.backend.common.changes.domain.ChangeRequestNotFoundException;
import com.industrialhub.backend.common.changes.domain.ChangeStatus;
import com.industrialhub.backend.common.changes.domain.InvalidChangeStatusTransitionException;
import com.industrialhub.backend.common.changes.infrastructure.ChangeRequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class ReviewChangeRequestUseCase {

    private final ChangeRequestRepository changeRequestRepository;

    public ReviewChangeRequestUseCase(ChangeRequestRepository changeRequestRepository) {
        this.changeRequestRepository = changeRequestRepository;
    }

    @Transactional
    public ChangeRequestResponse execute(UUID id, ReviewChangeRequestRequest req, String principal) {
        ChangeRequest cr = changeRequestRepository.findById(id)
            .orElseThrow(() -> new ChangeRequestNotFoundException(id));

        if (cr.getStatus() != ChangeStatus.SUBMITTED) {
            throw new InvalidChangeStatusTransitionException(cr.getStatus(), ChangeStatus.UNDER_REVIEW);
        }

        cr.setStatus(ChangeStatus.UNDER_REVIEW);
        cr.setImpactAssessment(req.impactAssessment());
        cr.setReviewedBy(principal);
        cr.setReviewedAt(LocalDateTime.now());

        return ChangeRequestResponse.from(changeRequestRepository.save(cr));
    }
}
