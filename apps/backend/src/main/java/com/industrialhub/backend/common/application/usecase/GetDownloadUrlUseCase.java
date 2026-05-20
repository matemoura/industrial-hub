package com.industrialhub.backend.common.application.usecase;

import com.industrialhub.backend.common.application.dto.DownloadUrlResponse;
import com.industrialhub.backend.common.domain.AttachmentNotFoundException;
import com.industrialhub.backend.common.infrastructure.AttachmentRepository;
import com.industrialhub.backend.common.infrastructure.StorageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.time.Duration;
import java.util.UUID;

@Service
public class GetDownloadUrlUseCase {
    private final AttachmentRepository attachmentRepository;
    private final StorageService storageService;
    private final long ttlMinutes;

    public GetDownloadUrlUseCase(
            AttachmentRepository attachmentRepository,
            StorageService storageService,
            @Value("${app.storage.presign-ttl-minutes:15}") long ttlMinutes) {
        this.attachmentRepository = attachmentRepository;
        this.storageService = storageService;
        this.ttlMinutes = ttlMinutes;
    }

    public DownloadUrlResponse execute(UUID id) {
        var attachment = attachmentRepository.findById(id)
            .orElseThrow(() -> new AttachmentNotFoundException(id));
        String url = storageService.generatePresignedUrl(attachment.getStorageKey(), Duration.ofMinutes(ttlMinutes));
        return new DownloadUrlResponse(url, java.time.LocalDateTime.now().plusMinutes(ttlMinutes));
    }
}
