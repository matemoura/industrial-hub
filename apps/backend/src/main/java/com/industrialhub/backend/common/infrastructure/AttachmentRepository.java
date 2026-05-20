package com.industrialhub.backend.common.infrastructure;

import com.industrialhub.backend.common.domain.Attachment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface AttachmentRepository extends JpaRepository<Attachment, UUID> {
    List<Attachment> findByEntityTypeAndEntityIdOrderByUploadedAtDesc(String entityType, String entityId);
}
