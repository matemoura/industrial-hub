package com.industrialhub.backend.qms.complaints.application.usecase;

import com.industrialhub.backend.qms.complaints.application.dto.ComplaintResponse;
import com.industrialhub.backend.qms.complaints.application.dto.LinkNcRequest;
import com.industrialhub.backend.qms.complaints.domain.CustomerComplaint;
import com.industrialhub.backend.qms.complaints.domain.CustomerComplaintNotFoundException;
import com.industrialhub.backend.qms.complaints.infrastructure.CustomerComplaintRepository;
import com.industrialhub.backend.qms.domain.NcNotFoundException;
import com.industrialhub.backend.qms.infrastructure.NonConformanceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class LinkNcToComplaintUseCase {

    private final CustomerComplaintRepository complaintRepository;
    private final NonConformanceRepository ncRepository;

    public LinkNcToComplaintUseCase(CustomerComplaintRepository complaintRepository,
                                     NonConformanceRepository ncRepository) {
        this.complaintRepository = complaintRepository;
        this.ncRepository = ncRepository;
    }

    @Transactional
    public ComplaintResponse execute(UUID complaintId, LinkNcRequest req) {
        CustomerComplaint complaint = complaintRepository.findById(complaintId)
            .orElseThrow(() -> new CustomerComplaintNotFoundException(complaintId));

        if (!ncRepository.existsById(req.ncId())) {
            throw new NcNotFoundException(req.ncId());
        }

        complaint.setLinkedNcId(req.ncId());
        return ComplaintResponse.from(complaintRepository.save(complaint));
    }
}
