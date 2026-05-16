package com.industrialhub.backend.qms.application.usecase;

import com.industrialhub.backend.common.application.AuditService;
import com.industrialhub.backend.common.domain.AuditAction;
import com.industrialhub.backend.qms.application.dto.CreateRcaRequest;
import com.industrialhub.backend.qms.application.dto.RcaResponse;
import com.industrialhub.backend.qms.domain.NcNotFoundException;
import com.industrialhub.backend.qms.domain.NcStatus;
import com.industrialhub.backend.qms.domain.NonConformance;
import com.industrialhub.backend.qms.domain.RcaNotFoundException;
import com.industrialhub.backend.qms.domain.RcaNotAllowedException;
import com.industrialhub.backend.qms.domain.RootCauseAnalysis;
import com.industrialhub.backend.qms.infrastructure.NonConformanceRepository;
import com.industrialhub.backend.qms.infrastructure.RootCauseAnalysisRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Service
public class UpdateRcaUseCase {

    private final NonConformanceRepository ncRepository;
    private final RootCauseAnalysisRepository rcaRepository;
    private final AuditService auditService;

    public UpdateRcaUseCase(NonConformanceRepository ncRepository,
                            RootCauseAnalysisRepository rcaRepository,
                            AuditService auditService) {
        this.ncRepository = ncRepository;
        this.rcaRepository = rcaRepository;
        this.auditService = auditService;
    }

    @Transactional
    public RcaResponse execute(UUID ncId, CreateRcaRequest request, String username) {
        NonConformance nc = ncRepository.findById(ncId)
                .orElseThrow(() -> new NcNotFoundException(ncId));

        if (nc.getStatus() == NcStatus.CLOSED) {
            throw new RcaNotAllowedException("RCA não pode ser alterada após o fechamento da NC");
        }

        RootCauseAnalysis rca = rcaRepository.findByNonConformanceId(ncId)
                .orElseThrow(() -> new RcaNotFoundException(ncId));

        rca.setWhy1(request.why1());
        rca.setAnswer1(request.answer1());
        rca.setWhy2(request.why2());
        rca.setAnswer2(request.answer2());
        rca.setWhy3(request.why3());
        rca.setAnswer3(request.answer3());
        rca.setWhy4(request.why4());
        rca.setAnswer4(request.answer4());
        rca.setWhy5(request.why5());
        rca.setAnswer5(request.answer5());
        rca.setRootCause(request.rootCause());
        rca.setUpdatedAt(LocalDateTime.now());

        RootCauseAnalysis saved = rcaRepository.save(rca);

        auditService.log(username, AuditAction.RCA_UPDATED, "RootCauseAnalysis", saved.getId(),
                Map.of("ncId", ncId.toString()));

        return RcaResponse.from(saved);
    }
}
