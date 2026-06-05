package com.industrialhub.backend.training.application.usecase;

import com.industrialhub.backend.common.infrastructure.StorageService;
import com.industrialhub.backend.training.domain.TrainingRecord;
import com.industrialhub.backend.training.domain.TrainingRecordNotFoundException;
import com.industrialhub.backend.training.infrastructure.TrainingRecordRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class GetCertificateDownloadUrlUseCase {

    private final TrainingRecordRepository recordRepository;
    private final StorageService storageService;

    public GetCertificateDownloadUrlUseCase(TrainingRecordRepository recordRepository,
                                            StorageService storageService) {
        this.recordRepository = recordRepository;
        this.storageService = storageService;
    }

    public String execute(UUID recordId) {
        TrainingRecord record = recordRepository.findById(recordId)
            .orElseThrow(() -> new TrainingRecordNotFoundException(recordId));

        if (record.getCertificateStoragePath() == null) {
            throw new IllegalStateException("Registro não possui certificado anexado.");
        }

        return storageService.generatePresignedUrl(record.getCertificateStoragePath(),
            Duration.ofMinutes(15));
    }
}
