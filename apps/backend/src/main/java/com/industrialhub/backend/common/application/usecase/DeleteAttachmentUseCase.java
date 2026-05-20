package com.industrialhub.backend.common.application.usecase;

import com.industrialhub.backend.common.application.AuditService;
import com.industrialhub.backend.common.domain.AuditAction;
import com.industrialhub.backend.common.domain.AttachmentNotFoundException;
import com.industrialhub.backend.common.infrastructure.AttachmentRepository;
import com.industrialhub.backend.common.infrastructure.StorageException;
import com.industrialhub.backend.common.infrastructure.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeleteAttachmentUseCase {
    private final AttachmentRepository attachmentRepository;
    private final StorageService storageService;
    private final AuditService auditService;

    @Transactional
    public void execute(UUID id, String deletedBy) {
        var attachment = attachmentRepository.findById(id)
            .orElseThrow(() -> new AttachmentNotFoundException(id));
        try {
            storageService.delete(attachment.getStorageKey());
        } catch (StorageException ex) {
            log.warn("Falha ao remover objeto S3 key={}: {}", attachment.getStorageKey(), ex.getMessage());
        }
        attachmentRepository.delete(attachment);
        auditService.log(deletedBy, AuditAction.ATTACHMENT_DELETED, "Attachment", id.toString(),
            Map.of("entityType", attachment.getEntityType(), "file", attachment.getOriginalName()));
    }
}
