package com.industrialhub.backend.qms.ged.application.usecase;

import com.industrialhub.backend.qms.application.usecase.ListDocumentNonConformancesUseCase;
import com.industrialhub.backend.qms.ged.application.dto.DownloadUrlResponse;
import com.industrialhub.backend.qms.ged.presentation.GedController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GedControllerTest {

    @Mock private UploadDocumentUseCase uploadDocumentUseCase;
    @Mock private AddRevisionUseCase addRevisionUseCase;
    @Mock private TransitionDocumentStatusUseCase transitionDocumentStatusUseCase;
    @Mock private ListDocumentsUseCase listDocumentsUseCase;
    @Mock private GetDocumentUseCase getDocumentUseCase;
    @Mock private GedGetDownloadUrlUseCase getDownloadUrlUseCase;
    @Mock private ListDocumentNonConformancesUseCase listDocumentNonConformancesUseCase;

    private GedController controller;

    @BeforeEach
    void setUp() {
        controller = new GedController(
                uploadDocumentUseCase,
                addRevisionUseCase,
                transitionDocumentStatusUseCase,
                listDocumentsUseCase,
                getDocumentUseCase,
                getDownloadUrlUseCase,
                listDocumentNonConformancesUseCase);
    }

    // (a) download endpoint retorna URL pré-assinada com TTL 900s (15 min)
    @Test
    void getDownloadUrl_returnsPresignedUrlWithTtl900() {
        UUID docId = UUID.randomUUID();
        UUID revId = UUID.randomUUID();
        String expectedUrl = "http://minio:9000/ged/SOP-001/test.pdf?X-Amz-Signature=abc";
        DownloadUrlResponse expected = new DownloadUrlResponse(expectedUrl, 900L);

        when(getDownloadUrlUseCase.execute(docId, revId)).thenReturn(expected);

        DownloadUrlResponse result = controller.getDownloadUrl(docId, revId);

        assertThat(result.url()).isEqualTo(expectedUrl);
        assertThat(result.expiresInSeconds()).isEqualTo(900L);
    }

    // (b) método createDocument exige permissão canCreate no módulo QMS — verificado via @PreAuthorize
    @Test
    void createDocument_requiresCanCreateQmsPermission() throws NoSuchMethodException {
        Method createMethod = GedController.class.getMethod(
                "createDocument",
                com.industrialhub.backend.qms.ged.application.dto.CreateDocumentRequest.class,
                org.springframework.web.multipart.MultipartFile.class,
                java.security.Principal.class);

        PreAuthorize preAuthorize = createMethod.getAnnotation(PreAuthorize.class);
        assertThat(preAuthorize).isNotNull();
        assertThat(preAuthorize.value())
                .contains("@perm.canCreate")
                .contains("AppModule).QMS");
    }
}
