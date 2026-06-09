package com.industrialhub.backend.qms.complaints.application.usecase;

import com.industrialhub.backend.common.application.AuditService;
import com.industrialhub.backend.common.domain.AuditAction;
import com.industrialhub.backend.qms.complaints.application.dto.ComplaintResponse;
import com.industrialhub.backend.qms.complaints.application.dto.CreateComplaintRequest;
import com.industrialhub.backend.qms.complaints.domain.ComplaintCodeConflictException;
import com.industrialhub.backend.qms.complaints.domain.ComplaintStatus;
import com.industrialhub.backend.qms.complaints.domain.CustomerComplaint;
import com.industrialhub.backend.qms.complaints.infrastructure.CustomerComplaintRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
public class CreateComplaintUseCase {

    private final CustomerComplaintRepository complaintRepository;
    private final AuditService auditService;

    public CreateComplaintUseCase(CustomerComplaintRepository complaintRepository,
                                   AuditService auditService) {
        this.complaintRepository = complaintRepository;
        this.auditService = auditService;
    }

    @Transactional
    public ComplaintResponse execute(CreateComplaintRequest req, String principal) {
        int year = LocalDate.now().getYear();
        String prefix = "REC-" + year + "-";
        long count = complaintRepository.countByCodeStartingWith(prefix);
        String code = prefix + String.format("%03d", count + 1);

        CustomerComplaint complaint = CustomerComplaint.builder()
            .code(code)
            .title(req.title())
            .description(req.description())
            .source(req.source())
            .productCode(req.productCode())
            .batchNumber(req.batchNumber())
            .severity(req.severity())
            .status(ComplaintStatus.RECEIVED)
            .reportedDate(req.reportedDate())
            .reportedBy(req.reportedBy())
            .assignedTo(req.assignedTo())
            .createdBy(principal)
            .createdAt(LocalDateTime.now())
            .build();

        try {
            CustomerComplaint saved = complaintRepository.save(complaint);
            auditService.log(principal, AuditAction.COMPLAINT_CREATED, "CustomerComplaint", saved.getId(), null);
            return ComplaintResponse.from(saved);
        } catch (DataIntegrityViolationException e) {
            throw new ComplaintCodeConflictException(code);
        }
    }
}
