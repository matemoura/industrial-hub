package com.industrialhub.backend.qms.application.usecase;

import com.industrialhub.backend.common.application.AuditService;
import com.industrialhub.backend.common.domain.AuditAction;
import com.industrialhub.backend.qms.application.dto.CreateRcaRequest;
import com.industrialhub.backend.qms.application.dto.RcaResponse;
import com.industrialhub.backend.qms.domain.NcNotFoundException;
import com.industrialhub.backend.qms.domain.NcStatus;
import com.industrialhub.backend.qms.domain.NonConformance;
import com.industrialhub.backend.qms.domain.RcaAlreadyExistsException;
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
public class CreateRcaUseCase {

    private final NonConformanceRepository ncRepository;
    private final RootCauseAnalysisRepository rcaRepository;
    private final AuditService auditService;

    public CreateRcaUseCase(NonConformanceRepository ncRepository,
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

        if (nc.getStatus() == NcStatus.OPEN) {
            throw new RcaNotAllowedException("RCA só pode ser criada após início da análise");
        }

        if (rcaRepository.existsByNonConformanceId(ncId)) {
            throw new RcaAlreadyExistsException();
        }

        RootCauseAnalysis rca = RootCauseAnalysis.builder()
                .nonConformance(nc)
                .why1(request.why1())
                .answer1(request.answer1())
                .why2(request.why2())
                .answer2(request.answer2())
                .why3(request.why3())
                .answer3(request.answer3())
                .why4(request.why4())
                .answer4(request.answer4())
                .why5(request.why5())
                .answer5(request.answer5())
                .rootCause(request.rootCause())
                .createdBy(username)
                .createdAt(LocalDateTime.now())
                .build();

        RootCauseAnalysis saved = rcaRepository.save(rca);

        auditService.log(username, AuditAction.RCA_CREATED, "RootCauseAnalysis", saved.getId(),
                Map.of("ncId", ncId.toString()));

        return RcaResponse.from(saved);
    }
}
