package com.industrialhub.backend.common.application.usecase;

import com.industrialhub.backend.common.application.AuditService;
import com.industrialhub.backend.common.application.dto.AttachmentResponse;
import com.industrialhub.backend.common.domain.Attachment;
import com.industrialhub.backend.common.domain.AttachmentEntityType;
import com.industrialhub.backend.common.domain.AuditAction;
import com.industrialhub.backend.common.domain.FileTooLargeException;
import com.industrialhub.backend.common.domain.InvalidFileTypeException;
import com.industrialhub.backend.common.infrastructure.AttachmentRepository;
import com.industrialhub.backend.common.infrastructure.StorageException;
import com.industrialhub.backend.common.infrastructure.StorageService;
import lombok.RequiredArgsConstructor;
import org.apache.tika.Tika;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UploadAttachmentUseCase {

    private static final Logger log = LoggerFactory.getLogger(UploadAttachmentUseCase.class);

    private static final Set<String> ALLOWED_TYPES = Set.of(
        "image/jpeg", "image/png", "image/webp", "application/pdf",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "application/vnd.ms-excel"
    );
    private static final long MAX_SIZE = 10L * 1024 * 1024; // 10 MB
    private static final Tika TIKA = new Tika();

    private final StorageService storageService;
    private final AttachmentRepository attachmentRepository;
    private final AuditService auditService;

    public AttachmentResponse execute(AttachmentEntityType entityType, UUID entityId, MultipartFile file, String uploadedBy) {
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new InvalidFileTypeException("missing");
        }
        String safeFilename = sanitizeFilename(new java.io.File(originalFilename).getName());
        if (safeFilename.isBlank()) {
            throw new InvalidFileTypeException("missing");
        }

        if (file.isEmpty()) {
            throw new InvalidFileTypeException("empty");
        }

        // SEC-073: explicit size check before reading content
        if (file.getSize() > MAX_SIZE) {
            throw new FileTooLargeException(MAX_SIZE);
        }

        // SEC-071: load bytes once, detect MIME via magic bytes (Tika)
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException ex) {
            throw new StorageException("Falha ao ler arquivo para validação", ex);
        }

        String detectedType = TIKA.detect(bytes, safeFilename);
        if (!ALLOWED_TYPES.contains(detectedType)) {
            throw new InvalidFileTypeException(detectedType);
        }

        // Log if declared content type diverges from actual
        String declaredType = file.getContentType();
        if (declaredType != null && !declaredType.equals(detectedType)) {
            log.warn("Content-Type mismatch for upload by {}: declared={}, detected={}",
                uploadedBy, declaredType, detectedType);
        }

        String key = entityType.toStoragePrefix() + "/" + entityId + "/" + UUID.randomUUID() + "-" + safeFilename;
        String contentType = detectedType;

        storageService.upload(key, new ByteArrayInputStream(bytes), contentType, bytes.length);

        Attachment attachment = Attachment.builder()
            .entityType(entityType.name())
            .entityId(entityId.toString())
            .storageKey(key)
            .originalName(originalFilename) // preserve original name for user display
            .contentType(contentType)
            .fileSizeBytes(file.getSize())
            .uploadedBy(uploadedBy)
            .uploadedAt(LocalDateTime.now())
            .build();

        try {
            Attachment saved = attachmentRepository.save(attachment);
            auditService.log(uploadedBy, AuditAction.ATTACHMENT_UPLOADED, "Attachment",
                saved.getId().toString(),
                Map.of("entityType", entityType.name(), "entityId", entityId.toString(),
                       "file", safeFilename));
            return AttachmentResponse.from(saved);
        } catch (Exception ex) {
            try { storageService.delete(key); } catch (StorageException ignored) {}
            throw ex;
        }
    }

    private static String sanitizeFilename(String name) {
        String sanitized = name.replaceAll("[^a-zA-Z0-9._\\-]", "_");
        return sanitized.length() > 100 ? sanitized.substring(0, 100) : sanitized;
    }
}
