package com.industrialhub.backend.qms.ged.application.usecase;

import com.industrialhub.backend.common.infrastructure.StorageService;
import com.industrialhub.backend.qms.ged.application.dto.DocumentRevisionResponse;
import com.industrialhub.backend.qms.ged.domain.Document;
import com.industrialhub.backend.qms.ged.domain.DocumentCategory;
import com.industrialhub.backend.qms.ged.domain.DocumentRevision;
import com.industrialhub.backend.qms.ged.domain.DocumentStatus;
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

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AddRevisionUseCaseTest {

    @Mock private DocumentRepository documentRepository;
    @Mock private DocumentRevisionRepository revisionRepository;
    @Mock private StorageService storageService;
    @Mock private GedFileValidator gedFileValidator;

    private AddRevisionUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new AddRevisionUseCase(documentRepository, revisionRepository,
                storageService, gedFileValidator);
    }

    @Test
    void addRevision_success() throws Exception {
        UUID docId = UUID.randomUUID();

        DocumentRevision firstRevision = DocumentRevision.builder()
            .id(UUID.randomUUID()).revisionNumber("1.0")
            .storagePath("ged/SOP-001/uuid_sop-001.pdf").originalFileName("sop-001.pdf")
            .fileSizeBytes(8L).uploadedBy("user1").uploadedAt(LocalDateTime.now().minusDays(1))
            .changeReason("Criação inicial").build();

        Document document = Document.builder()
            .id(docId).code("SOP-001").title("Procedimento Operacional")
            .category(DocumentCategory.SOP).status(DocumentStatus.PUBLISHED)
            .createdBy("user1").createdAt(LocalDateTime.now().minusDays(1))
            .currentRevision(firstRevision).build();

        when(documentRepository.findById(docId)).thenReturn(Optional.of(document));
        when(revisionRepository.findRevisionNumbersByDocumentId(docId)).thenReturn(List.of("1.0"));

        UUID newRevId = UUID.randomUUID();
        DocumentRevision newRevision = DocumentRevision.builder()
            .id(newRevId).document(document).revisionNumber("2.0")
            .storagePath("ged/SOP-001/uuid_sop-001-v2.pdf").originalFileName("sop-001-v2.pdf")
            .fileSizeBytes(10L).uploadedBy("user1").uploadedAt(LocalDateTime.now())
            .changeReason("Atualização do procedimento").build();

        when(revisionRepository.save(any(DocumentRevision.class))).thenReturn(newRevision);
        when(documentRepository.save(any(Document.class))).thenReturn(document);

        byte[] fileContent = "conteudo v2".getBytes();
        MockMultipartFile file = new MockMultipartFile(
            "file", "sop-001-v2.pdf", "application/pdf", fileContent
        );

        DocumentRevisionResponse response = useCase.execute(
                docId, "Atualização do procedimento", file, "user1");

        assertThat(response).isNotNull();
        assertThat(response.revisionNumber()).isEqualTo("2.0");
        assertThat(response.originalFileName()).isEqualTo("sop-001-v2.pdf");

        verify(storageService).upload(anyString(), any(), eq("application/pdf"),
                eq((long) fileContent.length));
        verify(revisionRepository).save(any(DocumentRevision.class));
        verify(documentRepository).save(document);
    }

    @Test
    void addRevision_obsoleteDocument_throwsException() {
        UUID docId = UUID.randomUUID();

        Document obsoleteDocument = Document.builder()
            .id(docId).code("SOP-001").title("Procedimento Obsoleto")
            .category(DocumentCategory.SOP).status(DocumentStatus.OBSOLETE)
            .createdBy("user1").createdAt(LocalDateTime.now().minusDays(30)).build();

        when(documentRepository.findById(docId)).thenReturn(Optional.of(obsoleteDocument));

        MockMultipartFile file = new MockMultipartFile(
            "file", "sop-001-v3.pdf", "application/pdf", "conteudo".getBytes()
        );

        // SEC-125: validator is called first (mocked to pass), then status check
        assertThatThrownBy(() -> useCase.execute(docId, "Tentativa de revisão", file, "user1"))
            .isInstanceOf(InvalidGedTransitionException.class)
            .hasMessageContaining("Documento obsoleto não aceita novas revisões");

        verify(storageService, never()).upload(any(), any(), any(), anyLong());
        verify(revisionRepository, never()).save(any());
    }
}
