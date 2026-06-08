package com.industrialhub.backend.common.changes.application.usecase;

import com.industrialhub.backend.common.changes.application.dto.ChangeRequestDetailResponse;
import com.industrialhub.backend.common.changes.application.dto.ChangeRequestLinkResponse;
import com.industrialhub.backend.common.changes.domain.ChangeRequest;
import com.industrialhub.backend.common.changes.domain.ChangeRequestNotFoundException;
import com.industrialhub.backend.common.changes.infrastructure.ChangeRequestLinkRepository;
import com.industrialhub.backend.common.changes.infrastructure.ChangeRequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class GetChangeRequestDetailUseCase {

    private final ChangeRequestRepository changeRequestRepository;
    private final ChangeRequestLinkRepository linkRepository;

    public GetChangeRequestDetailUseCase(ChangeRequestRepository changeRequestRepository,
                                          ChangeRequestLinkRepository linkRepository) {
        this.changeRequestRepository = changeRequestRepository;
        this.linkRepository = linkRepository;
    }

    @Transactional(readOnly = true)
    public ChangeRequestDetailResponse execute(UUID id) {
        ChangeRequest cr = changeRequestRepository.findById(id)
            .orElseThrow(() -> new ChangeRequestNotFoundException(id));

        List<ChangeRequestLinkResponse> links = linkRepository.findByChangeRequestId(id)
            .stream()
            .map(ChangeRequestLinkResponse::from)
            .toList();

        return ChangeRequestDetailResponse.from(cr, links);
    }
}
