package com.industrialhub.backend.training.application.usecase;

import com.industrialhub.backend.common.application.AuditService;
import com.industrialhub.backend.common.domain.AuditAction;
import com.industrialhub.backend.training.application.dto.TrainingRecordResponse;
import com.industrialhub.backend.training.domain.EffectivenessResult;
import com.industrialhub.backend.training.domain.TrainingRecord;
import com.industrialhub.backend.training.domain.TrainingRecordNotFoundException;
import com.industrialhub.backend.training.infrastructure.TrainingRecordRepository;
import jakarta.validation.constraints.NotNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

@Service
public class AssessEffectivenessUseCase {

    private final TrainingRecordRepository recordRepository;
    private final AuditService auditService;

    public AssessEffectivenessUseCase(TrainingRecordRepository recordRepository,
                                      AuditService auditService) {
        this.recordRepository = recordRepository;
        this.auditService = auditService;
    }

    public record Request(
        @NotNull EffectivenessResult result,
        String notes
    ) {}

    @Transactional
    public TrainingRecordResponse execute(UUID recordId, Request req, String principal) {
        TrainingRecord record = recordRepository.findById(recordId)
            .orElseThrow(() -> new TrainingRecordNotFoundException(recordId));

        if (!record.isPassed()) {
            throw new IllegalStateException(
                "Não é possível avaliar eficácia de registro com passed=false.");
        }

        if (record.getEffectivenessResult() != null) {
            throw new IllegalStateException(
                "Eficácia já foi avaliada para este registro.");
        }

        record.setEffectivenessAssessedAt(LocalDate.now());
        record.setEffectivenessAssessedBy(principal);
        record.setEffectivenessResult(req.result());
        record.setEffectivenessNotes(req.notes());

        auditService.log(principal, AuditAction.TRAINING_EFFECTIVENESS_ASSESSED, "TrainingRecord",
            recordId, Map.of("result", req.result().name(),
                             "courseCode", record.getCourse().getCode()));

        return TrainingRecordResponse.from(record);
    }
}
