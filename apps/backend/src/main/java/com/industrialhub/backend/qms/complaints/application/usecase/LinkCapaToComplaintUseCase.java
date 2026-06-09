package com.industrialhub.backend.qms.complaints.application.usecase;

import com.industrialhub.backend.qms.complaints.application.dto.ComplaintResponse;
import com.industrialhub.backend.qms.complaints.application.dto.LinkCapaRequest;
import com.industrialhub.backend.qms.complaints.domain.CustomerComplaint;
import com.industrialhub.backend.qms.complaints.domain.CustomerComplaintNotFoundException;
import com.industrialhub.backend.qms.complaints.infrastructure.CustomerComplaintRepository;
import com.industrialhub.backend.qms.domain.ActionNotFoundException;
import com.industrialhub.backend.qms.infrastructure.CorrectiveActionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class LinkCapaToComplaintUseCase {

    private final CustomerComplaintRepository complaintRepository;
    private final CorrectiveActionRepository capaRepository;

    public LinkCapaToComplaintUseCase(CustomerComplaintRepository complaintRepository,
                                       CorrectiveActionRepository capaRepository) {
        this.complaintRepository = complaintRepository;
        this.capaRepository = capaRepository;
    }

    @Transactional
    public ComplaintResponse execute(UUID complaintId, LinkCapaRequest req) {
        CustomerComplaint complaint = complaintRepository.findById(complaintId)
            .orElseThrow(() -> new CustomerComplaintNotFoundException(complaintId));

        if (!capaRepository.existsById(req.capaId())) {
            throw new ActionNotFoundException(req.capaId());
        }

        complaint.setLinkedCapaId(req.capaId());
        return ComplaintResponse.from(complaintRepository.save(complaint));
    }
}
