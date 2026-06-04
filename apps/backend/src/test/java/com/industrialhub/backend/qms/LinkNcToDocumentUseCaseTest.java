package com.industrialhub.backend.qms;

import com.industrialhub.backend.common.application.AuditService;
import com.industrialhub.backend.qms.application.dto.LinkNcToDocumentRequest;
import com.industrialhub.backend.qms.application.dto.NcDocumentLinkResponse;
import com.industrialhub.backend.qms.application.usecase.LinkNcToDocumentUseCase;
import com.industrialhub.backend.qms.domain.NcDocumentLink;
import com.industrialhub.backend.qms.domain.NcDocumentLinkAlreadyExistsException;
import com.industrialhub.backend.qms.domain.NcDocumentLinkType;
import com.industrialhub.backend.qms.domain.NcNotFoundException;
import com.industrialhub.backend.qms.domain.NcSeverity;
import com.industrialhub.backend.qms.domain.NcStatus;
import com.industrialhub.backend.qms.domain.NcType;
import com.industrialhub.backend.qms.domain.NonConformance;
import com.industrialhub.backend.qms.ged.domain.Document;
import com.industrialhub.backend.qms.ged.domain.DocumentCategory;
import com.industrialhub.backend.qms.ged.domain.DocumentNotFoundException;
import com.industrialhub.backend.qms.ged.domain.DocumentStatus;
import com.industrialhub.backend.qms.ged.infrastructure.DocumentRepository;
import com.industrialhub.backend.qms.infrastructure.NcDocumentLinkRepository;
import com.industrialhub.backend.qms.infrastructure.NonConformanceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
class LinkNcToDocumentUseCaseTest {

    @Mock
    private NonConformanceRepository nonConformanceRepository;

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private NcDocumentLinkRepository ncDocumentLinkRepository;

    @Mock
    private AuditService auditService;

    private LinkNcToDocumentUseCase useCase;

    private UUID ncId;
    private UUID documentId;
    private NonConformance nc;
    private Document document;

    @BeforeEach
    void setUp() {
        useCase = new LinkNcToDocumentUseCase(
                nonConformanceRepository, documentRepository, ncDocumentLinkRepository, auditService);

        ncId = UUID.randomUUID();
        documentId = UUID.randomUUID();

        nc = NonConformance.builder()
                .id(ncId)
                .title("NC Teste")
                .type(NcType.PROCESS)
                .severity(NcSeverity.MEDIUM)
                .status(NcStatus.OPEN)
                .reportedBy("user1")
                .reportedAt(LocalDateTime.now())
                .build();

        document = Document.builder()
                .id(documentId)
                .code("SOP-001")
                .title("Procedimento de Teste")
                .category(DocumentCategory.SOP)
                .status(DocumentStatus.PUBLISHED)
                .createdBy("user1")
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    void execute_validLink_returnsResponse() {
        // Given
        LinkNcToDocumentRequest req = new LinkNcToDocumentRequest(
                documentId, NcDocumentLinkType.PROCEDURE_AT_OCCURRENCE);

        when(nonConformanceRepository.findById(ncId)).thenReturn(Optional.of(nc));
        when(documentRepository.findById(documentId)).thenReturn(Optional.of(document));

        UUID linkId = UUID.randomUUID();
        NcDocumentLink savedLink = NcDocumentLink.builder()
                .id(linkId)
                .nonConformance(nc)
                .document(document)
                .linkType(NcDocumentLinkType.PROCEDURE_AT_OCCURRENCE)
                .linkedBy("user1")
                .linkedAt(LocalDateTime.now())
                .build();

        when(ncDocumentLinkRepository.saveAndFlush(any())).thenReturn(savedLink);

        // When
        NcDocumentLinkResponse response = useCase.execute(ncId, req, "user1");

        // Then
        assertThat(response).isNotNull();
        assertThat(response.documentCode()).isEqualTo("SOP-001");
        assertThat(response.documentStatus()).isEqualTo(DocumentStatus.PUBLISHED);
        assertThat(response.linkType()).isEqualTo(NcDocumentLinkType.PROCEDURE_AT_OCCURRENCE);
        verify(auditService).log(eq("user1"),
                eq(com.industrialhub.backend.common.domain.AuditAction.NC_DOCUMENT_LINKED),
                eq("NcDocumentLink"), any(UUID.class), any());
    }

    @Test
    void execute_ncNotFound_throwsNcNotFoundException() {
        when(nonConformanceRepository.findById(ncId)).thenReturn(Optional.empty());

        LinkNcToDocumentRequest req = new LinkNcToDocumentRequest(
                documentId, NcDocumentLinkType.OTHER);

        assertThatThrownBy(() -> useCase.execute(ncId, req, "user1"))
                .isInstanceOf(NcNotFoundException.class);
    }

    @Test
    void execute_documentNotFound_throwsDocumentNotFoundException() {
        when(nonConformanceRepository.findById(ncId)).thenReturn(Optional.of(nc));
        when(documentRepository.findById(documentId)).thenReturn(Optional.empty());

        LinkNcToDocumentRequest req = new LinkNcToDocumentRequest(
                documentId, NcDocumentLinkType.OTHER);

        assertThatThrownBy(() -> useCase.execute(ncId, req, "user1"))
                .isInstanceOf(DocumentNotFoundException.class);
    }

    @Test
    void execute_obsoleteDocument_throwsIllegalArgumentException() {
        Document obsoleteDoc = Document.builder()
                .id(documentId)
                .code("SOP-OLD")
                .title("Documento Obsoleto")
                .category(DocumentCategory.SOP)
                .status(DocumentStatus.OBSOLETE)
                .createdBy("user1")
                .createdAt(LocalDateTime.now())
                .build();

        when(nonConformanceRepository.findById(ncId)).thenReturn(Optional.of(nc));
        when(documentRepository.findById(documentId)).thenReturn(Optional.of(obsoleteDoc));

        LinkNcToDocumentRequest req = new LinkNcToDocumentRequest(
                documentId, NcDocumentLinkType.PROCEDURE_AT_OCCURRENCE);

        assertThatThrownBy(() -> useCase.execute(ncId, req, "user1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("OBSOLETE");
    }

    @Test
    void execute_duplicateLink_throwsNcDocumentLinkAlreadyExistsException() {
        when(nonConformanceRepository.findById(ncId)).thenReturn(Optional.of(nc));
        when(documentRepository.findById(documentId)).thenReturn(Optional.of(document));
        when(ncDocumentLinkRepository.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("uk_nc_document_link"));

        LinkNcToDocumentRequest req = new LinkNcToDocumentRequest(
                documentId, NcDocumentLinkType.CORRECTIVE_REFERENCE);

        assertThatThrownBy(() -> useCase.execute(ncId, req, "user1"))
                .isInstanceOf(NcDocumentLinkAlreadyExistsException.class);
    }

}
