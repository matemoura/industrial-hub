package com.industrialhub.backend.qms.complaints.application.usecase;

import com.industrialhub.backend.qms.complaints.application.dto.ComplaintResponse;
import com.industrialhub.backend.qms.complaints.domain.CustomerComplaint;
import com.industrialhub.backend.qms.complaints.domain.CustomerComplaintNotFoundException;
import com.industrialhub.backend.qms.complaints.infrastructure.CustomerComplaintRepository;
import com.industrialhub.backend.qms.domain.NonConformance;
import com.industrialhub.backend.qms.infrastructure.CorrectiveActionRepository;
import com.industrialhub.backend.qms.infrastructure.NonConformanceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class GetComplaintDetailUseCase {

    private final CustomerComplaintRepository complaintRepository;
    private final NonConformanceRepository ncRepository;
    private final CorrectiveActionRepository capaRepository;

    public GetComplaintDetailUseCase(CustomerComplaintRepository complaintRepository,
                                      NonConformanceRepository ncRepository,
                                      CorrectiveActionRepository capaRepository) {
        this.complaintRepository = complaintRepository;
        this.ncRepository = ncRepository;
        this.capaRepository = capaRepository;
    }

    @Transactional(readOnly = true)
    public ComplaintResponse execute(UUID id) {
        CustomerComplaint complaint = complaintRepository.findById(id)
            .orElseThrow(() -> new CustomerComplaintNotFoundException(id));

        String linkedNcCode = null;
        if (complaint.getLinkedNcId() != null) {
            linkedNcCode = ncRepository.findById(complaint.getLinkedNcId())
                .map(NonConformance::getTitle)
                .orElse(null);
        }

        String linkedCapaDescription = null;
        if (complaint.getLinkedCapaId() != null) {
            linkedCapaDescription = capaRepository.findById(complaint.getLinkedCapaId())
                .map(ca -> ca.getDescription())
                .orElse(null);
        }

        return ComplaintResponse.from(complaint, linkedNcCode, linkedCapaDescription);
    }
}
