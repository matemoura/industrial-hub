package com.industrialhub.backend.common.application.usecase;

import com.industrialhub.backend.common.application.AuditService;
import com.industrialhub.backend.common.application.dto.AttachmentResponse;
import com.industrialhub.backend.common.domain.Attachment;
import com.industrialhub.backend.common.domain.AuditAction;
import com.industrialhub.backend.common.domain.InvalidFileTypeException;
import com.industrialhub.backend.common.infrastructure.AttachmentRepository;
import com.industrialhub.backend.common.infrastructure.StorageException;
import com.industrialhub.backend.common.infrastructure.StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UploadAttachmentUseCase {

    private static final Set<String> ALLOWED_TYPES = Set.of(
        "image/jpeg", "image/png", "image/webp", "application/pdf",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "application/vnd.ms-excel"
    );
    private static final long MAX_SIZE = 10 * 1024 * 1024; // 10 MB

    private final StorageService storageService;
    private final AttachmentRepository attachmentRepository;
    private final AuditService auditService;

    public AttachmentResponse execute(String entityType, UUID entityId, MultipartFile file, String uploadedBy) {
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new InvalidFileTypeException("missing");
        }
        String safeFilename = new java.io.File(originalFilename).getName();
        if (safeFilename.isBlank()) {
            throw new InvalidFileTypeException("missing");
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType)) {
            throw new InvalidFileTypeException(contentType);
        }

        if (file.isEmpty()) {
            throw new InvalidFileTypeException("empty");
        }

        String key = entityType.toLowerCase() + "/" + entityId + "/" + UUID.randomUUID() + "-" + safeFilename;

        try {
            storageService.upload(key, file.getInputStream(), contentType, file.getSize());
        } catch (IOException ex) {
            throw new StorageException("Falha ao ler arquivo para upload", ex);
        }

        Attachment attachment = Attachment.builder()
            .entityType(entityType)
            .entityId(entityId.toString())
            .storageKey(key)
            .originalName(safeFilename)
            .contentType(contentType)
            .fileSizeBytes(file.getSize())
            .uploadedBy(uploadedBy)
            .uploadedAt(LocalDateTime.now())
            .build();

        try {
            Attachment saved = attachmentRepository.save(attachment);
            auditService.log(uploadedBy, AuditAction.ATTACHMENT_UPLOADED, "Attachment",
                saved.getId().toString(),
                Map.of("entityType", entityType, "entityId", entityId.toString(),
                       "file", safeFilename));
            return AttachmentResponse.from(saved);
        } catch (Exception ex) {
            try { storageService.delete(key); } catch (StorageException ignored) {}
            throw ex;
        }
    }
}
