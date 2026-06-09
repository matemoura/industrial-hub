package com.industrialhub.backend.qms.complaints.application.usecase;

import com.industrialhub.backend.qms.complaints.application.dto.ComplaintResponse;
import com.industrialhub.backend.qms.complaints.domain.ComplaintStatus;
import com.industrialhub.backend.qms.complaints.infrastructure.CustomerComplaintRepository;
import com.industrialhub.backend.qms.domain.NcSeverity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
public class GetComplaintsUseCase {

    private final CustomerComplaintRepository complaintRepository;

    public GetComplaintsUseCase(CustomerComplaintRepository complaintRepository) {
        this.complaintRepository = complaintRepository;
    }

    @Transactional(readOnly = true)
    public Page<ComplaintResponse> execute(ComplaintStatus status, NcSeverity severity,
                                            String productCode, Boolean reportedToAnvisa,
                                            LocalDate from, LocalDate to, Pageable pageable) {
        return complaintRepository.findByFilters(status, severity, productCode, reportedToAnvisa, from, to, pageable)
            .map(ComplaintResponse::from);
    }
}
