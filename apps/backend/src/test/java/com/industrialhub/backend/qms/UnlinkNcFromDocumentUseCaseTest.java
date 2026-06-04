package com.industrialhub.backend.qms;

import com.industrialhub.backend.common.application.AuditService;
import com.industrialhub.backend.common.domain.AuditAction;
import com.industrialhub.backend.qms.application.usecase.UnlinkNcFromDocumentUseCase;
import com.industrialhub.backend.qms.domain.NcDocumentLink;
import com.industrialhub.backend.qms.domain.NcDocumentLinkType;
import com.industrialhub.backend.qms.domain.NcNotFoundException;
import com.industrialhub.backend.qms.domain.NcSeverity;
import com.industrialhub.backend.qms.domain.NcStatus;
import com.industrialhub.backend.qms.domain.NcType;
import com.industrialhub.backend.qms.domain.NonConformance;
import com.industrialhub.backend.qms.ged.domain.Document;
import com.industrialhub.backend.qms.ged.domain.DocumentCategory;
import com.industrialhub.backend.qms.ged.domain.DocumentStatus;
import com.industrialhub.backend.qms.infrastructure.NcDocumentLinkRepository;
import com.industrialhub.backend.qms.infrastructure.NonConformanceRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UnlinkNcFromDocumentUseCaseTest {

    @Mock
    private NonConformanceRepository nonConformanceRepository;

    @Mock
    private NcDocumentLinkRepository ncDocumentLinkRepository;

    @Mock
    private AuditService auditService;

    private UnlinkNcFromDocumentUseCase useCase;

    private UUID ncId;
    private UUID documentId;
    private NonConformance nc;
    private Document doc;

    @BeforeEach
    void setUp() {
        useCase = new UnlinkNcFromDocumentUseCase(nonConformanceRepository, ncDocumentLinkRepository, auditService);
        ncId = UUID.randomUUID();
        documentId = UUID.randomUUID();

        nc = NonConformance.builder()
                .id(ncId).title("NC").type(NcType.PROCESS)
                .severity(NcSeverity.LOW).status(NcStatus.OPEN)
                .reportedBy("user").reportedAt(LocalDateTime.now()).build();

        doc = Document.builder()
                .id(documentId).code("SOP-001").title("Doc")
                .category(DocumentCategory.SOP).status(DocumentStatus.PUBLISHED)
                .createdBy("user").createdAt(LocalDateTime.now()).build();
    }

    @Test
    void execute_existingLink_deletesSuccessfully() {
        NcDocumentLink link = new NcDocumentLink(nc, doc, NcDocumentLinkType.OTHER, "user");

        when(nonConformanceRepository.existsById(ncId)).thenReturn(true);
        when(ncDocumentLinkRepository.findByNonConformanceIdAndDocumentId(ncId, documentId))
                .thenReturn(Optional.of(link));

        useCase.execute(ncId, documentId, "user");

        verify(ncDocumentLinkRepository).delete(link);
        verify(auditService).log(eq("user"), eq(AuditAction.NC_DOCUMENT_UNLINKED),
                eq("NcDocumentLink"), eq(ncId), any());
    }

    @Test
    void execute_ncNotFound_throwsNcNotFoundException() {
        when(nonConformanceRepository.existsById(ncId)).thenReturn(false);

        assertThatThrownBy(() -> useCase.execute(ncId, documentId, "user"))
                .isInstanceOf(NcNotFoundException.class);
    }

    @Test
    void execute_linkNotFound_throwsEntityNotFoundException() {
        when(nonConformanceRepository.existsById(ncId)).thenReturn(true);
        when(ncDocumentLinkRepository.findByNonConformanceIdAndDocumentId(ncId, documentId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(ncId, documentId, "user"))
                .isInstanceOf(EntityNotFoundException.class);
    }
}
