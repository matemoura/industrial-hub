package com.industrialhub.backend.qms.application.usecase;

import com.industrialhub.backend.qms.application.dto.CAPASummaryResponse;
import com.industrialhub.backend.qms.domain.ActionStatus;
import com.industrialhub.backend.qms.domain.ActionType;
import com.industrialhub.backend.qms.infrastructure.CorrectiveActionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class ListCAPAsUseCase {

    private final CorrectiveActionRepository correctiveActionRepository;

    public ListCAPAsUseCase(CorrectiveActionRepository correctiveActionRepository) {
        this.correctiveActionRepository = correctiveActionRepository;
    }

    public Page<CAPASummaryResponse> execute(ActionType type, ActionStatus status, UUID ncId, Pageable pageable) {
        return correctiveActionRepository.findAllCapas(type, status, ncId, pageable);
    }
}
