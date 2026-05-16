package com.industrialhub.backend.qms.application.usecase;

import com.industrialhub.backend.qms.application.dto.RcaResponse;
import com.industrialhub.backend.qms.domain.RcaNotFoundException;
import com.industrialhub.backend.qms.infrastructure.RootCauseAnalysisRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class GetRcaByNcUseCase {

    private final RootCauseAnalysisRepository rcaRepository;

    public GetRcaByNcUseCase(RootCauseAnalysisRepository rcaRepository) {
        this.rcaRepository = rcaRepository;
    }

    @Transactional(readOnly = true)
    public RcaResponse execute(UUID ncId) {
        return rcaRepository.findByNonConformanceId(ncId)
                .map(RcaResponse::from)
                .orElseThrow(() -> new RcaNotFoundException(ncId));
    }
}
