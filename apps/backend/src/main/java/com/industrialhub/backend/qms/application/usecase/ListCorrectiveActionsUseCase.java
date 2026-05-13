package com.industrialhub.backend.qms.application.usecase;

import com.industrialhub.backend.qms.application.dto.ActionResponse;
import com.industrialhub.backend.qms.domain.NcNotFoundException;
import com.industrialhub.backend.qms.infrastructure.CorrectiveActionRepository;
import com.industrialhub.backend.qms.infrastructure.NonConformanceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class ListCorrectiveActionsUseCase {

    private final NonConformanceRepository ncRepository;
    private final CorrectiveActionRepository actionRepository;

    public ListCorrectiveActionsUseCase(NonConformanceRepository ncRepository,
                                        CorrectiveActionRepository actionRepository) {
        this.ncRepository = ncRepository;
        this.actionRepository = actionRepository;
    }

    @Transactional(readOnly = true)
    public List<ActionResponse> execute(UUID ncId) {
        if (!ncRepository.existsById(ncId)) {
            throw new NcNotFoundException(ncId);
        }
        return actionRepository.findByNonConformanceId(ncId).stream()
                .map(ActionResponse::from)
                .toList();
    }
}
