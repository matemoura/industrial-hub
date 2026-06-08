package com.industrialhub.backend.common.changes.application.usecase;

import com.industrialhub.backend.common.changes.application.dto.ChangeRequestResponse;
import com.industrialhub.backend.common.changes.domain.ChangeStatus;
import com.industrialhub.backend.common.changes.domain.ChangeType;
import com.industrialhub.backend.common.changes.infrastructure.ChangeRequestRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class GetChangeRequestsUseCase {

    private final ChangeRequestRepository changeRequestRepository;

    public GetChangeRequestsUseCase(ChangeRequestRepository changeRequestRepository) {
        this.changeRequestRepository = changeRequestRepository;
    }

    @Transactional(readOnly = true)
    public Page<ChangeRequestResponse> execute(
            ChangeStatus status,
            ChangeType changeType,
            String requestedBy,
            LocalDateTime from,
            LocalDateTime to,
            boolean pendingForMe,
            String role,
            Pageable pageable) {

        ChangeStatus effectiveStatus = status;

        if (pendingForMe) {
            // SUPERVISOR sees SUBMITTED, ADMIN sees UNDER_REVIEW
            if ("ADMIN".equals(role)) {
                effectiveStatus = ChangeStatus.UNDER_REVIEW;
            } else {
                effectiveStatus = ChangeStatus.SUBMITTED;
            }
        }

        return changeRequestRepository.findByFilters(
            effectiveStatus, changeType, requestedBy, from, to, pageable
        ).map(ChangeRequestResponse::from);
    }

    /**
     * Returns pending count filtered by role (SEC-179):
     * - ADMIN: counts UNDER_REVIEW (awaiting admin approval)
     * - SUPERVISOR: counts SUBMITTED (awaiting supervisor review)
     * - OPERATOR: counts own DRAFT + SUBMITTED
     */
    @Transactional(readOnly = true)
    public long countPendingForRole(String role, String username) {
        return switch (role) {
            case "ADMIN" -> changeRequestRepository.countByStatusIn(
                List.of(ChangeStatus.UNDER_REVIEW));
            case "SUPERVISOR" -> changeRequestRepository.countByStatusIn(
                List.of(ChangeStatus.SUBMITTED));
            default -> // OPERATOR
                changeRequestRepository.countByRequestedByAndStatusIn(
                    username, List.of(ChangeStatus.DRAFT, ChangeStatus.SUBMITTED));
        };
    }
}
