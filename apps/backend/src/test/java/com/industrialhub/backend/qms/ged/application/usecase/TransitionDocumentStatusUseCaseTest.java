package com.industrialhub.backend.qms.ged.application.usecase;

import com.industrialhub.backend.qms.ged.application.dto.DocumentResponse;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransitionDocumentStatusUseCaseTest {

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private DocumentRevisionRepository revisionRepository;

    private TransitionDocumentStatusUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new TransitionDocumentStatusUseCase(documentRepository, revisionRepository);
    }

    private Document buildDocument(UUID id, DocumentStatus status, DocumentRevision revision) {
        return Document.builder()
            .id(id)
            .code("SOP-001")
            .title("Procedimento Operacional")
            .category(DocumentCategory.SOP)
            .status(status)
            .createdBy("user1")
            .createdAt(LocalDateTime.now().minusDays(1))
            .currentRevision(revision)
            .build();
    }

    private DocumentRevision buildRevision(Document document) {
        return DocumentRevision.builder()
            .id(UUID.randomUUID())
            .document(document)
            .revisionNumber("1.0")
            .storagePath("ged/SOP-001/uuid_sop-001.pdf")
            .originalFileName("sop-001.pdf")
            .fileSizeBytes(8L)
            .uploadedBy("user1")
            .uploadedAt(LocalDateTime.now().minusDays(1))
            .changeReason("Criação inicial")
            .build();
    }

    @Test
    void transition_draftToPublished_success() {
        // Arrange
        UUID docId = UUID.randomUUID();
        Document document = buildDocument(docId, DocumentStatus.DRAFT, null);
        DocumentRevision revision = buildRevision(document);
        document.setCurrentRevision(revision);

        when(documentRepository.findById(docId)).thenReturn(Optional.of(document));
        when(documentRepository.save(any(Document.class))).thenAnswer(inv -> inv.getArgument(0));
        when(revisionRepository.findByDocumentIdOrderByUploadedAtDesc(docId)).thenReturn(List.of(revision));

        // Act
        DocumentResponse response = useCase.execute(docId, DocumentStatus.PUBLISHED);

        // Assert
        assertThat(response.status()).isEqualTo(DocumentStatus.PUBLISHED);
        verify(documentRepository).save(document);
    }

    @Test
    void transition_publishedToObsolete_success() {
        // Arrange
        UUID docId = UUID.randomUUID();
        Document document = buildDocument(docId, DocumentStatus.PUBLISHED, null);
        DocumentRevision revision = buildRevision(document);
        document.setCurrentRevision(revision);

        when(documentRepository.findById(docId)).thenReturn(Optional.of(document));
        when(documentRepository.save(any(Document.class))).thenAnswer(inv -> inv.getArgument(0));
        when(revisionRepository.findByDocumentIdOrderByUploadedAtDesc(docId)).thenReturn(List.of(revision));

        // Act
        DocumentResponse response = useCase.execute(docId, DocumentStatus.OBSOLETE);

        // Assert
        assertThat(response.status()).isEqualTo(DocumentStatus.OBSOLETE);
        verify(documentRepository).save(document);
    }

    @Test
    void transition_obsoleteToAny_throwsException() {
        // Arrange
        UUID docId = UUID.randomUUID();
        Document document = buildDocument(docId, DocumentStatus.OBSOLETE, null);

        when(documentRepository.findById(docId)).thenReturn(Optional.of(document));

        // Act & Assert
        assertThatThrownBy(() -> useCase.execute(docId, DocumentStatus.DRAFT))
            .isInstanceOf(InvalidGedTransitionException.class)
            .hasMessageContaining("Transição inválida");

        assertThatThrownBy(() -> useCase.execute(docId, DocumentStatus.PUBLISHED))
            .isInstanceOf(InvalidGedTransitionException.class)
            .hasMessageContaining("Transição inválida");

        verify(documentRepository, never()).save(any());
    }

    @Test
    void transition_draftWithoutRevision_throwsException() {
        // Arrange
        UUID docId = UUID.randomUUID();
        Document document = buildDocument(docId, DocumentStatus.DRAFT, null);
        // currentRevision = null

        when(documentRepository.findById(docId)).thenReturn(Optional.of(document));

        // Act & Assert
        assertThatThrownBy(() -> useCase.execute(docId, DocumentStatus.PUBLISHED))
            .isInstanceOf(InvalidGedTransitionException.class)
            .hasMessageContaining("Documento sem revisão não pode ser publicado");

        verify(documentRepository, never()).save(any());
    }
}
