package com.industrialhub.backend.qms.ged.application.usecase;

import com.industrialhub.backend.common.infrastructure.StorageService;
import com.industrialhub.backend.qms.ged.application.dto.CreateDocumentRequest;
import com.industrialhub.backend.qms.ged.application.dto.DocumentResponse;
import com.industrialhub.backend.qms.ged.domain.Document;
import com.industrialhub.backend.qms.ged.domain.DocumentCategory;
import com.industrialhub.backend.qms.ged.domain.DocumentRevision;
import com.industrialhub.backend.qms.ged.domain.DocumentStatus;
import com.industrialhub.backend.qms.ged.infrastructure.DocumentRepository;
import com.industrialhub.backend.qms.ged.infrastructure.DocumentRevisionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UploadDocumentUseCaseTest {

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private DocumentRevisionRepository revisionRepository;

    @Mock
    private StorageService storageService;

    private UploadDocumentUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new UploadDocumentUseCase(documentRepository, revisionRepository, storageService);
    }

    @Test
    void createDocument_success() throws Exception {
        // Arrange
        CreateDocumentRequest request = new CreateDocumentRequest(
            "SOP-001", "Procedimento Operacional", DocumentCategory.SOP, "Criação inicial"
        );
        MockMultipartFile file = new MockMultipartFile(
            "file", "sop-001.pdf", "application/pdf", "conteudo".getBytes()
        );

        when(documentRepository.existsByCode("SOP-001")).thenReturn(false);

        UUID docId = UUID.randomUUID();
        Document savedDocument = Document.builder()
            .id(docId)
            .code("SOP-001")
            .title("Procedimento Operacional")
            .category(DocumentCategory.SOP)
            .status(DocumentStatus.DRAFT)
            .createdBy("user1")
            .createdAt(LocalDateTime.now())
            .build();

        UUID revId = UUID.randomUUID();
        DocumentRevision savedRevision = DocumentRevision.builder()
            .id(revId)
            .document(savedDocument)
            .revisionNumber("1.0")
            .storagePath("ged/SOP-001/uuid_sop-001.pdf")
            .originalFileName("sop-001.pdf")
            .fileSizeBytes(8L)
            .uploadedBy("user1")
            .uploadedAt(LocalDateTime.now())
            .changeReason("Criação inicial")
            .build();

        when(documentRepository.save(any(Document.class)))
            .thenAnswer(inv -> {
                Document d = inv.getArgument(0);
                if (d.getId() == null) {
                    Document withId = Document.builder()
                        .id(docId)
                        .code(d.getCode())
                        .title(d.getTitle())
                        .category(d.getCategory())
                        .status(d.getStatus())
                        .createdBy(d.getCreatedBy())
                        .createdAt(d.getCreatedAt())
                        .currentRevision(d.getCurrentRevision())
                        .build();
                    return withId;
                }
                d.setCurrentRevision(savedRevision);
                return d;
            });

        when(revisionRepository.save(any(DocumentRevision.class))).thenReturn(savedRevision);

        // Act
        DocumentResponse response = useCase.execute(request, file, "user1");

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.code()).isEqualTo("SOP-001");
        assertThat(response.status()).isEqualTo(DocumentStatus.DRAFT);
        assertThat(response.currentRevision()).isNotNull();
        assertThat(response.currentRevision().revisionNumber()).isEqualTo("1.0");

        verify(storageService).upload(anyString(), any(), eq("application/pdf"), eq(8L));
        verify(documentRepository, times(2)).save(any(Document.class));
        verify(revisionRepository).save(any(DocumentRevision.class));
    }

    @Test
    void createDocument_duplicateCode_throwsException() {
        // Arrange
        CreateDocumentRequest request = new CreateDocumentRequest(
            "SOP-001", "Procedimento Operacional", DocumentCategory.SOP, "Criação inicial"
        );
        MockMultipartFile file = new MockMultipartFile(
            "file", "sop-001.pdf", "application/pdf", "conteudo".getBytes()
        );

        when(documentRepository.existsByCode("SOP-001")).thenReturn(true);

        // Act & Assert
        assertThatThrownBy(() -> useCase.execute(request, file, "user1"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Código de documento já existe: SOP-001");

        verify(storageService, never()).upload(any(), any(), any(), anyLong());
        verify(documentRepository, never()).save(any());
    }

    @Test
    void createDocument_emptyFile_throwsException() {
        // Arrange
        CreateDocumentRequest request = new CreateDocumentRequest(
            "SOP-001", "Procedimento Operacional", DocumentCategory.SOP, "Criação inicial"
        );
        MockMultipartFile emptyFile = new MockMultipartFile(
            "file", "empty.pdf", "application/pdf", new byte[0]
        );

        // Act & Assert
        assertThatThrownBy(() -> useCase.execute(request, emptyFile, "user1"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Arquivo é obrigatório");

        verify(documentRepository, never()).existsByCode(any());
        verify(storageService, never()).upload(any(), any(), any(), anyLong());
    }
}
