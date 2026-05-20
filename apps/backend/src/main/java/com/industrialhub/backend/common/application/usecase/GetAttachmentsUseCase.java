package com.industrialhub.backend.common.application.usecase;

import com.industrialhub.backend.common.application.dto.AttachmentResponse;
import com.industrialhub.backend.common.domain.AttachmentEntityType;
import com.industrialhub.backend.common.infrastructure.AttachmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GetAttachmentsUseCase {
    private final AttachmentRepository attachmentRepository;

    public List<AttachmentResponse> execute(AttachmentEntityType entityType, UUID entityId) {
        return attachmentRepository
            .findByEntityTypeAndEntityIdOrderByUploadedAtDesc(entityType.name(), entityId.toString())
            .stream()
            .map(AttachmentResponse::from)
            .toList();
    }
}
