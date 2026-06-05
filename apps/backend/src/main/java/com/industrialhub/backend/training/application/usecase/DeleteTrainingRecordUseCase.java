package com.industrialhub.backend.training.application.usecase;

import com.industrialhub.backend.common.application.AuditService;
import com.industrialhub.backend.common.domain.AuditAction;
import com.industrialhub.backend.common.infrastructure.StorageService;
import com.industrialhub.backend.training.domain.TrainingRecord;
import com.industrialhub.backend.training.domain.TrainingRecordNotFoundException;
import com.industrialhub.backend.training.infrastructure.TrainingRecordRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
public class DeleteTrainingRecordUseCase {

    private final TrainingRecordRepository recordRepository;
    private final StorageService storageService;
    private final AuditService auditService;

    public DeleteTrainingRecordUseCase(TrainingRecordRepository recordRepository,
                                       StorageService storageService,
                                       AuditService auditService) {
        this.recordRepository = recordRepository;
        this.storageService = storageService;
        this.auditService = auditService;
    }

    @Transactional
    public void execute(UUID id, String principal) {
        TrainingRecord record = recordRepository.findById(id)
            .orElseThrow(() -> new TrainingRecordNotFoundException(id));

        if (record.getCertificateStoragePath() != null) {
            storageService.delete(record.getCertificateStoragePath());
        }

        recordRepository.delete(record);

        auditService.log(principal, AuditAction.TRAINING_RECORD_DELETED, "TrainingRecord",
            id, Map.of("courseCode", record.getCourse().getCode(), "username", record.getUsername()));
    }
}
