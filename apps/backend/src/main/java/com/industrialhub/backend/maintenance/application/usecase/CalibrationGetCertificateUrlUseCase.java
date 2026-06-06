package com.industrialhub.backend.maintenance.application.usecase;

import com.industrialhub.backend.common.infrastructure.StorageService;
import com.industrialhub.backend.maintenance.domain.CalibrationRecord;
import com.industrialhub.backend.maintenance.domain.CalibrationScheduleNotFoundException;
import com.industrialhub.backend.maintenance.infrastructure.CalibrationRecordRepository;
import com.industrialhub.backend.qms.ged.application.usecase.GedGetDownloadUrlUseCase;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.UUID;

@Service
public class CalibrationGetCertificateUrlUseCase {

    private final CalibrationRecordRepository recordRepository;
    private final StorageService storageService;
    private final GedGetDownloadUrlUseCase gedGetDownloadUrlUseCase;

    public CalibrationGetCertificateUrlUseCase(CalibrationRecordRepository recordRepository,
                                               StorageService storageService,
                                               GedGetDownloadUrlUseCase gedGetDownloadUrlUseCase) {
        this.recordRepository = recordRepository;
        this.storageService = storageService;
        this.gedGetDownloadUrlUseCase = gedGetDownloadUrlUseCase;
    }

    public record DownloadUrlResult(String url, long expiresInSeconds) {}

    @Transactional(readOnly = true)
    public DownloadUrlResult execute(UUID recordId) {
        CalibrationRecord record = recordRepository.findById(recordId)
            .orElseThrow(() -> new CalibrationScheduleNotFoundException(recordId));

        boolean hasCertificate = record.getCertificateDocumentId() != null
            || record.getCertificateStoragePath() != null;

        if (!hasCertificate) {
            throw new CalibrationScheduleNotFoundException(recordId);
        }

        if (record.getCertificateDocumentId() != null) {
            UUID docId = record.getCertificateDocumentId();
            var response = gedGetDownloadUrlUseCase.execute(docId, docId);
            return new DownloadUrlResult(response.url(), response.expiresInSeconds());
        }

        String url = storageService.generatePresignedUrl(record.getCertificateStoragePath(),
            Duration.ofMinutes(15));
        return new DownloadUrlResult(url, 900L);
    }
}
