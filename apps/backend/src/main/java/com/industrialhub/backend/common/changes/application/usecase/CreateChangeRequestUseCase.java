package com.industrialhub.backend.common.changes.application.usecase;

import com.industrialhub.backend.common.application.AuditService;
import com.industrialhub.backend.common.changes.application.dto.ChangeRequestResponse;
import com.industrialhub.backend.common.changes.application.dto.CreateChangeRequestRequest;
import com.industrialhub.backend.common.changes.domain.ChangeRequest;
import com.industrialhub.backend.common.changes.domain.ChangeRequestCodeConflictException;
import com.industrialhub.backend.common.changes.domain.ChangeStatus;
import com.industrialhub.backend.common.changes.infrastructure.ChangeRequestRepository;
import com.industrialhub.backend.common.domain.AuditAction;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
public class CreateChangeRequestUseCase {

    private final ChangeRequestRepository changeRequestRepository;
    private final AuditService auditService;

    public CreateChangeRequestUseCase(ChangeRequestRepository changeRequestRepository,
                                      AuditService auditService) {
        this.changeRequestRepository = changeRequestRepository;
        this.auditService = auditService;
    }

    @Transactional
    public ChangeRequestResponse execute(CreateChangeRequestRequest req, String principal) {
        int year = LocalDate.now().getYear();
        String prefix = "CR-" + year + "-";
        long count = changeRequestRepository.countByCodeStartingWith(prefix);
        String code = prefix + String.format("%03d", count + 1);

        ChangeRequest cr = ChangeRequest.builder()
            .code(code)
            .title(req.title())
            .description(req.description())
            .changeType(req.changeType())
            .justification(req.justification())
            .status(ChangeStatus.DRAFT)
            .requestedBy(principal)
            .build();

        try {
            ChangeRequest saved = changeRequestRepository.save(cr);
            auditService.log(principal, AuditAction.CR_CREATED, "ChangeRequest", saved.getId(), null);
            return ChangeRequestResponse.from(saved);
        } catch (DataIntegrityViolationException e) {
            throw new ChangeRequestCodeConflictException(code);
        }
    }
}
