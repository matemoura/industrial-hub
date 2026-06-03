package com.industrialhub.backend.qms.ged;

import com.industrialhub.backend.common.infrastructure.StorageService;
import com.industrialhub.backend.qms.ged.application.dto.DocumentRevisionResponse;
import com.industrialhub.backend.qms.ged.application.usecase.AddRevisionUseCase;
import com.industrialhub.backend.qms.ged.application.usecase.GedFileValidator;
import com.industrialhub.backend.qms.ged.domain.Document;
import com.industrialhub.backend.qms.ged.domain.DocumentCategory;
import com.industrialhub.backend.qms.ged.domain.DocumentRevision;
import com.industrialhub.backend.qms.ged.domain.DocumentStatus;
import com.industrialhub.backend.qms.ged.domain.InvalidGedFileException;
import com.industrialhub.backend.qms.ged.domain.InvalidGedTransitionException;
import com.industrialhub.backend.qms.ged.infrastructure.DocumentRepository;
import com.industrialhub.backend.qms.ged.infrastructure.DocumentRevisionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AddRevisionUseCaseTest {

    @Mock private DocumentRepository documentRepository;
    @Mock private DocumentRevisionRepository revisionRepository;
    @Mock private StorageService storageService;
    @Mock private GedFileValidator gedFileValidator;

    private AddRevisionUseCase useCase;

    private UUID docId;
    private Document document;

    @BeforeEach
    void setUp() {
        useCase = new AddRevisionUseCase(documentRepository, revisionRepository,
                storageService, gedFileValidator);
        docId = UUID.randomUUID();
        document = Document.builder()
                .id(docId)
                .code("SOP-001")
                .title("Procedimento A")
                .category(DocumentCategory.SOP)
                .status(DocumentStatus.PUBLISHED)
                .createdBy("admin")
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    void execute_secondRevision_hasRevisionNumber2() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "v2.pdf", "application/pdf", new byte[]{0x25, 0x50, 0x44, 0x46});

        DocumentRevision savedRev = DocumentRevision.builder()
                .id(UUID.randomUUID()).document(document)
                .revisionNumber("2.0").storagePath("ged/SOP-001/x_v2.pdf")
                .originalFileName("v2.pdf").fileSizeBytes(4L)
                .uploadedBy("supervisor").uploadedAt(LocalDateTime.now())
                .changeReason("Atualização").build();

        when(documentRepository.findById(docId)).thenReturn(Optional.of(document));
        when(revisionRepository.findRevisionNumbersByDocumentId(docId))
                .thenReturn(List.of("1.0"));
        when(revisionRepository.save(any())).thenReturn(savedRev);
        when(documentRepository.save(any())).thenReturn(document);

        DocumentRevisionResponse response = useCase.execute(docId, "Atualização", file, "supervisor");

        assertThat(response.revisionNumber()).isEqualTo("2.0");
    }

    @Test
    void execute_obsoleteDocument_throwsInvalidGedTransitionException() {
        document.setStatus(DocumentStatus.OBSOLETE);

        MockMultipartFile file = new MockMultipartFile(
                "file", "doc.pdf", "application/pdf", new byte[]{0x25, 0x50, 0x44, 0x46});

        when(documentRepository.findById(docId)).thenReturn(Optional.of(document));

        assertThatThrownBy(() -> useCase.execute(docId, "Motivo", file, "supervisor"))
                .isInstanceOf(InvalidGedTransitionException.class)
                .hasMessageContaining("obsoleto");
    }

    @Test
    void execute_invalidFile_throwsBeforeStorageAccess() {
        doThrow(new InvalidGedFileException("Tipo não permitido"))
                .when(gedFileValidator).validate(any());

        MockMultipartFile file = new MockMultipartFile(
                "file", "malicious.html", "text/html", "<html/>".getBytes());

        assertThatThrownBy(() -> useCase.execute(docId, "Motivo", file, "supervisor"))
                .isInstanceOf(InvalidGedFileException.class);

        verifyNoInteractions(storageService);
        verifyNoInteractions(documentRepository);
    }

    @Test
    void execute_pathTraversalFilename_sanitizedInStoragePath() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "../../secret.pdf", "application/pdf", new byte[]{0x25, 0x50, 0x44, 0x46});

        DocumentRevision savedRev = DocumentRevision.builder()
                .id(UUID.randomUUID()).document(document)
                .revisionNumber("2.0").storagePath("ged/SOP-001/x_secret.pdf")
                .originalFileName("secret.pdf").fileSizeBytes(4L)
                .uploadedBy("supervisor").uploadedAt(LocalDateTime.now())
                .changeReason("Motivo").build();

        when(documentRepository.findById(docId)).thenReturn(Optional.of(document));
        when(revisionRepository.findRevisionNumbersByDocumentId(docId)).thenReturn(List.of("1.0"));
        when(revisionRepository.save(any())).thenReturn(savedRev);
        when(documentRepository.save(any())).thenReturn(document);

        useCase.execute(docId, "Motivo", file, "supervisor");

        verify(storageService).upload(argThat(path -> {
            assertThat(path).doesNotContain("../");
            assertThat(path).contains("secret.pdf");
            return true;
        }), any(), any(), anyLong());
    }
}
