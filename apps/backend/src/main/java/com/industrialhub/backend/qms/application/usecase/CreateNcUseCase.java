package com.industrialhub.backend.qms.application.usecase;

import com.industrialhub.backend.qms.application.dto.CreateNcRequest;
import com.industrialhub.backend.qms.application.dto.NcResponse;
import com.industrialhub.backend.qms.domain.NonConformance;
import com.industrialhub.backend.qms.domain.NcSeverity;
import com.industrialhub.backend.qms.domain.NcStatus;
import com.industrialhub.backend.qms.infrastructure.NonConformanceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class CreateNcUseCase {

    private final NonConformanceRepository repository;
    private final QmsEmailService emailService;

    public CreateNcUseCase(NonConformanceRepository repository, QmsEmailService emailService) {
        this.repository = repository;
        this.emailService = emailService;
    }

    @Transactional
    public NcResponse execute(CreateNcRequest request, String reportedBy) {
        NonConformance nc = NonConformance.builder()
                .title(request.title())
                .description(request.description())
                .type(request.type())
                .severity(request.severity())
                .status(NcStatus.OPEN)
                .reportedBy(reportedBy)
                .reportedAt(LocalDateTime.now())
                .build();

        NonConformance saved = repository.save(nc);

        if (saved.getSeverity() == NcSeverity.CRITICAL) {
            emailService.notifyCriticalNc(saved);
        }

        return NcResponse.from(saved);
    }
}
