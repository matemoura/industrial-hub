package com.industrialhub.backend.common.application;

import com.industrialhub.backend.common.application.dto.AttachmentResponse;
import com.industrialhub.backend.common.application.usecase.UploadAttachmentUseCase;
import com.industrialhub.backend.common.domain.Attachment;
import com.industrialhub.backend.common.domain.AuditAction;
import com.industrialhub.backend.common.domain.InvalidFileTypeException;
import com.industrialhub.backend.common.infrastructure.AttachmentRepository;
import com.industrialhub.backend.common.infrastructure.StorageException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UploadAttachmentUseCaseTest {

    private InMemoryStorageService storageService;

    @Mock
    private AttachmentRepository attachmentRepository;

    @Mock
    private AuditService auditService;

    private UploadAttachmentUseCase useCase;

    @BeforeEach
    void setUp() {
        storageService = new InMemoryStorageService();
        useCase = new UploadAttachmentUseCase(storageService, attachmentRepository, auditService);
    }

    @Test
    void upload_success_returnsAttachmentResponse() {
        UUID entityId = UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile(
            "file", "test.pdf", "application/pdf", "content".getBytes());

        Attachment saved = Attachment.builder()
            .id(UUID.randomUUID())
            .entityType("work-order")
            .entityId(entityId.toString())
            .storageKey("work-order/" + entityId + "/test.pdf")
            .originalName("test.pdf")
            .contentType("application/pdf")
            .fileSizeBytes(7L)
            .uploadedBy("user1")
            .uploadedAt(java.time.LocalDateTime.now())
            .build();

        when(attachmentRepository.save(any(Attachment.class))).thenReturn(saved);

        AttachmentResponse response = useCase.execute("work-order", entityId, file, "user1");

        assertThat(response).isNotNull();
        assertThat(response.originalName()).isEqualTo("test.pdf");
        assertThat(response.contentType()).isEqualTo("application/pdf");
        assertThat(response.uploadedBy()).isEqualTo("user1");
        assertThat(response.entityType()).isEqualTo("work-order");
        verify(auditService).log(eq("user1"), eq(AuditAction.ATTACHMENT_UPLOADED),
            eq("Attachment"), anyString(), any(Map.class));
    }

    @Test
    void upload_nullFilename_throwsInvalidFileTypeException() {
        UUID entityId = UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile(
            "file", null, "application/pdf", "content".getBytes());

        assertThatThrownBy(() -> useCase.execute("work-order", entityId, file, "user1"))
            .isInstanceOf(InvalidFileTypeException.class)
            .hasMessageContaining("missing");

        verifyNoInteractions(attachmentRepository);
    }

    @Test
    void upload_blankFilename_throwsInvalidFileTypeException() {
        UUID entityId = UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile(
            "file", "   ", "application/pdf", "content".getBytes());

        assertThatThrownBy(() -> useCase.execute("work-order", entityId, file, "user1"))
            .isInstanceOf(InvalidFileTypeException.class)
            .hasMessageContaining("missing");

        verifyNoInteractions(attachmentRepository);
    }

    @Test
    void upload_emptyFile_throwsInvalidFileTypeException() {
        UUID entityId = UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile(
            "file", "empty.pdf", "application/pdf", new byte[0]);

        assertThatThrownBy(() -> useCase.execute("work-order", entityId, file, "user1"))
            .isInstanceOf(InvalidFileTypeException.class)
            .hasMessageContaining("empty");

        verifyNoInteractions(attachmentRepository);
    }

    @Test
    void upload_invalidContentType_throwsInvalidFileTypeException() {
        UUID entityId = UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile(
            "file", "test.exe", "application/octet-stream", "binary".getBytes());

        assertThatThrownBy(() -> useCase.execute("work-order", entityId, file, "user1"))
            .isInstanceOf(InvalidFileTypeException.class)
            .hasMessageContaining("application/octet-stream");

        verifyNoInteractions(attachmentRepository);
    }

    @Test
    void upload_storageException_propagatesWithoutSave() {
        InMemoryStorageService failingStorage = new InMemoryStorageService() {
            @Override
            public void upload(String key, java.io.InputStream content, String contentType, long sizeBytes) {
                throw new StorageException("S3 unavailable", new RuntimeException());
            }
        };
        UploadAttachmentUseCase failingUseCase = new UploadAttachmentUseCase(failingStorage, attachmentRepository, auditService);

        UUID entityId = UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile(
            "file", "test.pdf", "application/pdf", "content".getBytes());

        assertThatThrownBy(() -> failingUseCase.execute("work-order", entityId, file, "user1"))
            .isInstanceOf(StorageException.class);

        verifyNoInteractions(attachmentRepository);
    }

    @Test
    void upload_dbFailsAfterStorageUpload_callsDeleteOnStorage() {
        // Track uploaded keys via a spy-like approach
        InMemoryStorageService spyStorage = spy(new InMemoryStorageService());
        UploadAttachmentUseCase spyUseCase = new UploadAttachmentUseCase(spyStorage, attachmentRepository, auditService);

        UUID entityId = UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile(
            "file", "doc.pdf", "application/pdf", "data".getBytes());

        when(attachmentRepository.save(any())).thenThrow(new RuntimeException("DB error"));

        assertThatThrownBy(() -> spyUseCase.execute("work-order", entityId, file, "user1"))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("DB error");

        // Verify best-effort cleanup: delete was called on the storage
        verify(spyStorage).delete(anyString());
    }
}
