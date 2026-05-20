package com.industrialhub.backend.common.application;

import com.industrialhub.backend.common.application.usecase.DeleteAttachmentUseCase;
import com.industrialhub.backend.common.domain.Attachment;
import com.industrialhub.backend.common.domain.AttachmentNotFoundException;
import com.industrialhub.backend.common.domain.AuditAction;
import com.industrialhub.backend.common.infrastructure.AttachmentRepository;
import com.industrialhub.backend.common.infrastructure.StorageException;
import com.industrialhub.backend.common.infrastructure.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeleteAttachmentUseCaseTest {

    @Mock
    private AttachmentRepository attachmentRepository;

    @Mock
    private StorageService storageService;

    @Mock
    private AuditService auditService;

    private DeleteAttachmentUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new DeleteAttachmentUseCase(attachmentRepository, storageService, auditService);
    }

    private Attachment buildAttachment(UUID id) {
        return Attachment.builder()
            .id(id)
            .entityType("work-order")
            .entityId(UUID.randomUUID().toString())
            .storageKey("work-order/abc/file.pdf")
            .originalName("file.pdf")
            .contentType("application/pdf")
            .fileSizeBytes(100L)
            .uploadedBy("user1")
            .uploadedAt(LocalDateTime.now())
            .build();
    }

    @Test
    void delete_success_deletesStorageAndRepoAndAudits() {
        UUID id = UUID.randomUUID();
        Attachment attachment = buildAttachment(id);
        when(attachmentRepository.findById(id)).thenReturn(Optional.of(attachment));

        useCase.execute(id, "admin");

        verify(storageService).delete("work-order/abc/file.pdf");
        verify(attachmentRepository).delete(attachment);
        verify(auditService).log(eq("admin"), eq(AuditAction.ATTACHMENT_DELETED),
            eq("Attachment"), eq(id.toString()), any(Map.class));
    }

    @Test
    void delete_attachmentNotFound_throwsAttachmentNotFoundException() {
        UUID id = UUID.randomUUID();
        when(attachmentRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(id, "admin"))
            .isInstanceOf(AttachmentNotFoundException.class)
            .hasMessageContaining(id.toString());

        verifyNoInteractions(storageService);
    }

    @Test
    void delete_storageExceptionOnS3_logsWarnButStillDeletesFromRepo() {
        UUID id = UUID.randomUUID();
        Attachment attachment = buildAttachment(id);
        when(attachmentRepository.findById(id)).thenReturn(Optional.of(attachment));
        doThrow(new StorageException("S3 error", new RuntimeException()))
            .when(storageService).delete(anyString());

        // Should not throw
        useCase.execute(id, "admin");

        verify(attachmentRepository).delete(attachment);
        verify(auditService).log(eq("admin"), eq(AuditAction.ATTACHMENT_DELETED),
            eq("Attachment"), eq(id.toString()), any(Map.class));
    }
}
