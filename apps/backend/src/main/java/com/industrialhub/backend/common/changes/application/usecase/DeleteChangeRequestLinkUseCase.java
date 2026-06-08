package com.industrialhub.backend.common.changes.application.usecase;

import com.industrialhub.backend.common.changes.domain.ChangeRequestLinkNotFoundException;
import com.industrialhub.backend.common.changes.domain.ChangeRequestNotFoundException;
import com.industrialhub.backend.common.changes.infrastructure.ChangeRequestLinkRepository;
import com.industrialhub.backend.common.changes.infrastructure.ChangeRequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class DeleteChangeRequestLinkUseCase {

    private final ChangeRequestRepository changeRequestRepository;
    private final ChangeRequestLinkRepository linkRepository;

    public DeleteChangeRequestLinkUseCase(ChangeRequestRepository changeRequestRepository,
                                           ChangeRequestLinkRepository linkRepository) {
        this.changeRequestRepository = changeRequestRepository;
        this.linkRepository = linkRepository;
    }

    @Transactional
    public void execute(UUID changeRequestId, UUID linkId) {
        // Verify CR exists
        if (!changeRequestRepository.existsById(changeRequestId)) {
            throw new ChangeRequestNotFoundException(changeRequestId);
        }

        var link = linkRepository.findById(linkId)
            .orElseThrow(() -> new ChangeRequestLinkNotFoundException(linkId));

        // Verify link belongs to this CR
        if (!link.getChangeRequest().getId().equals(changeRequestId)) {
            throw new ChangeRequestLinkNotFoundException(linkId);
        }

        linkRepository.delete(link);
    }
}
