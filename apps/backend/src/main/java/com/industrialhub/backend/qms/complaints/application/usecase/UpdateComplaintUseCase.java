package com.industrialhub.backend.qms.complaints.application.usecase;

import com.industrialhub.backend.qms.complaints.application.dto.ComplaintResponse;
import com.industrialhub.backend.qms.complaints.application.dto.UpdateComplaintRequest;
import com.industrialhub.backend.qms.complaints.domain.ComplaintClosedException;
import com.industrialhub.backend.qms.complaints.domain.ComplaintStatus;
import com.industrialhub.backend.qms.complaints.domain.CustomerComplaint;
import com.industrialhub.backend.qms.complaints.domain.CustomerComplaintNotFoundException;
import com.industrialhub.backend.qms.complaints.infrastructure.CustomerComplaintRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class UpdateComplaintUseCase {

    private final CustomerComplaintRepository complaintRepository;

    public UpdateComplaintUseCase(CustomerComplaintRepository complaintRepository) {
        this.complaintRepository = complaintRepository;
    }

    @Transactional
    public ComplaintResponse execute(UUID id, UpdateComplaintRequest req) {
        CustomerComplaint complaint = complaintRepository.findById(id)
            .orElseThrow(() -> new CustomerComplaintNotFoundException(id));

        if (complaint.getStatus() == ComplaintStatus.CLOSED) {
            throw new ComplaintClosedException(id);
        }

        if (req.title() != null) complaint.setTitle(req.title());
        if (req.description() != null) complaint.setDescription(req.description());
        if (req.productCode() != null) complaint.setProductCode(req.productCode());
        if (req.batchNumber() != null) complaint.setBatchNumber(req.batchNumber());
        if (req.assignedTo() != null) complaint.setAssignedTo(req.assignedTo());
        if (req.investigationSummary() != null) complaint.setInvestigationSummary(req.investigationSummary());
        if (req.rootCause() != null) complaint.setRootCause(req.rootCause());
        if (req.correctiveAction() != null) complaint.setCorrectiveAction(req.correctiveAction());

        return ComplaintResponse.from(complaintRepository.save(complaint));
    }
}
