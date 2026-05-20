package com.industrialhub.backend.common.application;

import com.industrialhub.backend.common.application.dto.AttachmentResponse;
import com.industrialhub.backend.common.application.usecase.UploadAttachmentUseCase;
import com.industrialhub.backend.common.domain.Attachment;
import com.industrialhub.backend.common.domain.AttachmentEntityType;
import com.industrialhub.backend.common.domain.AuditAction;
import com.industrialhub.backend.common.domain.FileTooLargeException;
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

    // Minimal PDF magic bytes (%PDF-1.4 header)
    private static final byte[] PDF_BYTES = "%PDF-1.4 minimal content".getBytes();

    // Minimal JPEG magic bytes
    private static final byte[] JPEG_BYTES = new byte[]{
        (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0,
        0x00, 0x10, 0x4A, 0x46, 0x49, 0x46 // JFIF marker
    };

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
            "file", "test.pdf", "application/pdf", PDF_BYTES);

        Attachment saved = Attachment.builder()
            .id(UUID.randomUUID())
            .entityType(AttachmentEntityType.WORK_ORDER.name())
            .entityId(entityId.toString())
            .storageKey(AttachmentEntityType.WORK_ORDER.toStoragePrefix() + "/" + entityId + "/test.pdf")
            .originalName("test.pdf")
            .contentType("application/pdf")
            .fileSizeBytes((long) PDF_BYTES.length)
            .uploadedBy("user1")
            .uploadedAt(java.time.LocalDateTime.now())
            .build();

        when(attachmentRepository.save(any(Attachment.class))).thenReturn(saved);

        AttachmentResponse response = useCase.execute(AttachmentEntityType.WORK_ORDER, entityId, file, "user1");

        assertThat(response).isNotNull();
        assertThat(response.originalName()).isEqualTo("test.pdf");
        assertThat(response.contentType()).isEqualTo("application/pdf");
        assertThat(response.uploadedBy()).isEqualTo("user1");
        assertThat(response.entityType()).isEqualTo(AttachmentEntityType.WORK_ORDER.name());
        verify(auditService).log(eq("user1"), eq(AuditAction.ATTACHMENT_UPLOADED),
            eq("Attachment"), anyString(), any(Map.class));
    }

    @Test
    void upload_nullFilename_throwsInvalidFileTypeException() {
        UUID entityId = UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile(
            "file", null, "application/pdf", PDF_BYTES);

        assertThatThrownBy(() -> useCase.execute(AttachmentEntityType.WORK_ORDER, entityId, file, "user1"))
            .isInstanceOf(InvalidFileTypeException.class)
            .hasMessageContaining("missing");

        verifyNoInteractions(attachmentRepository);
    }

    @Test
    void upload_blankFilename_throwsInvalidFileTypeException() {
        UUID entityId = UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile(
            "file", "   ", "application/pdf", PDF_BYTES);

        assertThatThrownBy(() -> useCase.execute(AttachmentEntityType.WORK_ORDER, entityId, file, "user1"))
            .isInstanceOf(InvalidFileTypeException.class)
            .hasMessageContaining("missing");

        verifyNoInteractions(attachmentRepository);
    }

    @Test
    void upload_emptyFile_throwsInvalidFileTypeException() {
        UUID entityId = UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile(
            "file", "empty.pdf", "application/pdf", new byte[0]);

        assertThatThrownBy(() -> useCase.execute(AttachmentEntityType.WORK_ORDER, entityId, file, "user1"))
            .isInstanceOf(InvalidFileTypeException.class)
            .hasMessageContaining("empty");

        verifyNoInteractions(attachmentRepository);
    }

    @Test
    void upload_executableContent_throwsInvalidFileTypeException() {
        // EXE magic bytes (MZ header)
        byte[] exeBytes = new byte[]{'M', 'Z', 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};
        UUID entityId = UUID.randomUUID();
        // Attempt to disguise .exe as PDF — Tika should detect real type
        MockMultipartFile file = new MockMultipartFile(
            "file", "script.pdf", "application/pdf", exeBytes);

        assertThatThrownBy(() -> useCase.execute(AttachmentEntityType.WORK_ORDER, entityId, file, "user1"))
            .isInstanceOf(InvalidFileTypeException.class);

        verifyNoInteractions(attachmentRepository);
    }

    @Test
    void upload_fileTooLarge_throwsFileTooLargeException() {
        UUID entityId = UUID.randomUUID();
        // 10 MB + 1 byte
        byte[] bigContent = new byte[10 * 1024 * 1024 + 1];
        // Put PDF magic bytes at start so the size check comes first
        bigContent[0] = '%'; bigContent[1] = 'P'; bigContent[2] = 'D'; bigContent[3] = 'F';
        MockMultipartFile file = new MockMultipartFile(
            "file", "large.pdf", "application/pdf", bigContent);

        assertThatThrownBy(() -> useCase.execute(AttachmentEntityType.WORK_ORDER, entityId, file, "user1"))
            .isInstanceOf(FileTooLargeException.class)
            .hasMessageContaining("10 MB");

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
            "file", "test.pdf", "application/pdf", PDF_BYTES);

        assertThatThrownBy(() -> failingUseCase.execute(AttachmentEntityType.WORK_ORDER, entityId, file, "user1"))
            .isInstanceOf(StorageException.class);

        verifyNoInteractions(attachmentRepository);
    }

    @Test
    void upload_dbFailsAfterStorageUpload_callsDeleteOnStorage() {
        InMemoryStorageService spyStorage = spy(new InMemoryStorageService());
        UploadAttachmentUseCase spyUseCase = new UploadAttachmentUseCase(spyStorage, attachmentRepository, auditService);

        UUID entityId = UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile(
            "file", "doc.pdf", "application/pdf", PDF_BYTES);

        when(attachmentRepository.save(any())).thenThrow(new RuntimeException("DB error"));

        assertThatThrownBy(() -> spyUseCase.execute(AttachmentEntityType.WORK_ORDER, entityId, file, "user1"))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("DB error");

        verify(spyStorage).delete(anyString());
    }

    @Test
    void upload_jpegContentDeclaredAsPdf_acceptsBasedOnRealType() {
        // JPEG bytes declared as PDF — Tika detects image/jpeg → accepted (whitelist has it)
        UUID entityId = UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile(
            "file", "photo.pdf", "application/pdf", JPEG_BYTES);

        Attachment saved = Attachment.builder()
            .id(UUID.randomUUID())
            .entityType(AttachmentEntityType.WORK_ORDER.name())
            .entityId(entityId.toString())
            .storageKey(AttachmentEntityType.WORK_ORDER.toStoragePrefix() + "/" + entityId + "/photo.pdf")
            .originalName("photo.pdf")
            .contentType("image/jpeg")
            .fileSizeBytes((long) JPEG_BYTES.length)
            .uploadedBy("user1")
            .uploadedAt(java.time.LocalDateTime.now())
            .build();

        when(attachmentRepository.save(any(Attachment.class))).thenReturn(saved);

        // Should NOT throw — real type (JPEG) is in whitelist
        AttachmentResponse response = useCase.execute(AttachmentEntityType.WORK_ORDER, entityId, file, "user1");
        assertThat(response.contentType()).isEqualTo("image/jpeg");
    }
}
