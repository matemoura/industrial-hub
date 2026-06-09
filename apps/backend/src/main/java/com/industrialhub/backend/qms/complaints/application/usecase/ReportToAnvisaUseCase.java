package com.industrialhub.backend.qms.complaints.application.usecase;

import com.industrialhub.backend.common.application.AuditService;
import com.industrialhub.backend.common.domain.AuditAction;
import com.industrialhub.backend.qms.complaints.application.dto.AnvisaReportRequest;
import com.industrialhub.backend.qms.complaints.application.dto.ComplaintResponse;
import com.industrialhub.backend.qms.complaints.domain.CustomerComplaint;
import com.industrialhub.backend.qms.complaints.domain.CustomerComplaintNotFoundException;
import com.industrialhub.backend.qms.complaints.infrastructure.CustomerComplaintRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class ReportToAnvisaUseCase {

    private final CustomerComplaintRepository complaintRepository;
    private final AuditService auditService;

    public ReportToAnvisaUseCase(CustomerComplaintRepository complaintRepository,
                                  AuditService auditService) {
        this.complaintRepository = complaintRepository;
        this.auditService = auditService;
    }

    @Transactional
    public ComplaintResponse execute(UUID id, AnvisaReportRequest req, String principal) {
        CustomerComplaint complaint = complaintRepository.findById(id)
            .orElseThrow(() -> new CustomerComplaintNotFoundException(id));

        complaint.setReportedToAnvisa(true);
        complaint.setAnvisaReportNumber(req.reportNumber());
        complaint.setAnvisaReportDate(req.reportDate());

        CustomerComplaint saved = complaintRepository.save(complaint);
        auditService.log(principal, AuditAction.COMPLAINT_ANVISA_REPORTED, "CustomerComplaint", id,
            java.util.Map.of("reportNumber", req.reportNumber()));
        return ComplaintResponse.from(saved);
    }
}
