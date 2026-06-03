package com.industrialhub.backend.qms.ged;

import com.industrialhub.backend.common.infrastructure.StorageService;
import com.industrialhub.backend.qms.ged.application.dto.CreateDocumentRequest;
import com.industrialhub.backend.qms.ged.application.dto.DocumentResponse;
import com.industrialhub.backend.qms.ged.application.usecase.GedFileValidator;
import com.industrialhub.backend.qms.ged.application.usecase.UploadDocumentUseCase;
import com.industrialhub.backend.qms.ged.domain.DocumentCategory;
import com.industrialhub.backend.qms.ged.domain.DocumentCodeAlreadyExistsException;
import com.industrialhub.backend.qms.ged.domain.InvalidGedFileException;
import com.industrialhub.backend.qms.ged.infrastructure.DocumentRepository;
import com.industrialhub.backend.qms.ged.infrastructure.DocumentRevisionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockMultipartFile;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UploadDocumentUseCaseTest {

    @Mock private DocumentRepository documentRepository;
    @Mock private DocumentRevisionRepository revisionRepository;
    @Mock private StorageService storageService;
    @Mock private GedFileValidator gedFileValidator;

    private UploadDocumentUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new UploadDocumentUseCase(documentRepository, revisionRepository,
                storageService, gedFileValidator);
    }

    // --- SEC-125: GedFileValidator is called before any storage access ---

    @Test
    void execute_invalidFile_throwsBeforeStorageAccess() {
        doThrow(new InvalidGedFileException("Tipo não permitido"))
                .when(gedFileValidator).validate(any());

        MockMultipartFile file = new MockMultipartFile(
                "file", "malicious.html", "text/html", "<html/>".getBytes());

        CreateDocumentRequest request = new CreateDocumentRequest(
                "SOP-001", "Procedimento A", DocumentCategory.SOP, "Criação inicial");

        assertThatThrownBy(() -> useCase.execute(request, file, "admin"))
                .isInstanceOf(InvalidGedFileException.class);

        // StorageService must NOT have been called
        verifyNoInteractions(storageService);
    }

    // --- SEC-126: filename sanitization in storagePath ---

    @Test
    void execute_pathTraversalFilename_sanitizedInStoragePath() throws Exception {
        CreateDocumentRequest request = new CreateDocumentRequest(
                "SOP-001", "Procedimento A", DocumentCategory.SOP, "Criação inicial");

        MockMultipartFile file = new MockMultipartFile(
                "file", "../../etc/passwd.pdf", "application/pdf",
                new byte[]{0x25, 0x50, 0x44, 0x46}); // minimal PDF magic bytes

        // Validator passes (mocked — no exception)
        when(documentRepository.existsByCode("SOP-001")).thenReturn(false);

        com.industrialhub.backend.qms.ged.domain.Document savedDoc =
                com.industrialhub.backend.qms.ged.domain.Document.builder()
                        .id(UUID.randomUUID())
                        .code("SOP-001")
                        .title("Procedimento A")
                        .category(DocumentCategory.SOP)
                        .status(com.industrialhub.backend.qms.ged.domain.DocumentStatus.DRAFT)
                        .createdBy("admin")
                        .build();

        com.industrialhub.backend.qms.ged.domain.DocumentRevision savedRev =
                com.industrialhub.backend.qms.ged.domain.DocumentRevision.builder()
                        .id(UUID.randomUUID())
                        .document(savedDoc)
                        .revisionNumber("1.0")
                        .storagePath("ged/SOP-001/x_passwd.pdf")
                        .originalFileName("passwd.pdf")
                        .fileSizeBytes(4L)
                        .uploadedBy("admin")
                        .uploadedAt(java.time.LocalDateTime.now())
                        .changeReason("Criação inicial")
                        .build();

        when(documentRepository.save(any())).thenReturn(savedDoc);
        when(revisionRepository.save(any())).thenReturn(savedRev);
        savedDoc.setCurrentRevision(savedRev);

        useCase.execute(request, file, "admin");

        // Verify storageService.upload was called with a sanitized path (no ../)
        verify(storageService).upload(argThat(path -> {
            assertThat(path).doesNotContain("../");
            assertThat(path).contains("passwd.pdf");
            return true;
        }), any(), any(), anyLong());
    }

    @Test
    void sanitizeFilename_pathTraversal_returnsFilenameOnly() {
        assertThat(UploadDocumentUseCase.sanitizeFilename("../../etc/passwd.pdf"))
                .isEqualTo("passwd.pdf");
    }

    @Test
    void sanitizeFilename_nullOrBlank_returnsFallback() {
        assertThat(UploadDocumentUseCase.sanitizeFilename(null)).isEqualTo("file");
        assertThat(UploadDocumentUseCase.sanitizeFilename("   ")).isEqualTo("file");
    }

    @Test
    void sanitizeFilename_normalName_returnsUnchanged() {
        assertThat(UploadDocumentUseCase.sanitizeFilename("document.pdf"))
                .isEqualTo("document.pdf");
    }

    // --- SEC-129: DataIntegrityViolationException → DocumentCodeAlreadyExistsException ---

    @Test
    void execute_duplicateCode_dataIntegrityViolation_throwsDocumentCodeAlreadyExistsException() throws Exception {
        CreateDocumentRequest request = new CreateDocumentRequest(
                "SOP-001", "Procedimento A", DocumentCategory.SOP, "Criação inicial");

        MockMultipartFile file = new MockMultipartFile(
                "file", "doc.pdf", "application/pdf", new byte[]{0x25, 0x50, 0x44, 0x46});

        when(documentRepository.existsByCode("SOP-001")).thenReturn(false);
        when(documentRepository.save(any()))
                .thenThrow(new DataIntegrityViolationException("unique constraint violation"));

        assertThatThrownBy(() -> useCase.execute(request, file, "admin"))
                .isInstanceOf(DocumentCodeAlreadyExistsException.class)
                .hasMessageContaining("SOP-001");
    }
}
