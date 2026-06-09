package com.industrialhub.backend.qms.complaints.application.usecase;

import com.industrialhub.backend.common.application.AuditService;
import com.industrialhub.backend.common.domain.AuditAction;
import com.industrialhub.backend.qms.complaints.application.dto.ComplaintResponse;
import com.industrialhub.backend.qms.complaints.application.dto.UpdateComplaintStatusRequest;
import com.industrialhub.backend.qms.complaints.domain.ComplaintStatus;
import com.industrialhub.backend.qms.complaints.domain.CustomerComplaint;
import com.industrialhub.backend.qms.complaints.domain.CustomerComplaintNotFoundException;
import com.industrialhub.backend.qms.complaints.domain.InvalidComplaintStatusTransitionException;
import com.industrialhub.backend.qms.complaints.infrastructure.CustomerComplaintRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class TransitionComplaintStatusUseCase {

    private final CustomerComplaintRepository complaintRepository;
    private final AuditService auditService;

    public TransitionComplaintStatusUseCase(CustomerComplaintRepository complaintRepository,
                                             AuditService auditService) {
        this.complaintRepository = complaintRepository;
        this.auditService = auditService;
    }

    @Transactional
    public ComplaintResponse execute(UUID id, UpdateComplaintStatusRequest req, String principal) {
        CustomerComplaint complaint = complaintRepository.findById(id)
            .orElseThrow(() -> new CustomerComplaintNotFoundException(id));

        ComplaintStatus current = complaint.getStatus();
        ComplaintStatus target = req.status();

        validateTransition(complaint, current, target);

        complaint.setStatus(target);

        if (target == ComplaintStatus.CLOSED) {
            complaint.setClosedAt(LocalDateTime.now());
        }

        CustomerComplaint saved = complaintRepository.save(complaint);
        auditService.log(principal, AuditAction.COMPLAINT_STATUS_CHANGED, "CustomerComplaint", id,
            java.util.Map.of("from", current.name(), "to", target.name()));
        return ComplaintResponse.from(saved);
    }

    private void validateTransition(CustomerComplaint complaint, ComplaintStatus current, ComplaintStatus target) {
        boolean valid = switch (current) {
            case RECEIVED -> target == ComplaintStatus.UNDER_INVESTIGATION;
            case UNDER_INVESTIGATION -> target == ComplaintStatus.INVESTIGATION_COMPLETED;
            case INVESTIGATION_COMPLETED -> target == ComplaintStatus.CLOSED;
            case CLOSED -> false;
        };

        if (!valid) {
            throw new InvalidComplaintStatusTransitionException(
                "Transição inválida: " + current + " → " + target);
        }

        if (target == ComplaintStatus.CLOSED) {
            if (complaint.getInvestigationSummary() == null || complaint.getInvestigationSummary().isBlank()) {
                throw new InvalidComplaintStatusTransitionException(
                    "investigationSummary é obrigatório para fechar a reclamação");
            }
            if (complaint.getRootCause() == null || complaint.getRootCause().isBlank()) {
                throw new InvalidComplaintStatusTransitionException(
                    "rootCause é obrigatório para fechar a reclamação");
            }
        }
    }
}
