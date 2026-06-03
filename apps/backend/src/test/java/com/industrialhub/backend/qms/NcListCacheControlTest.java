package com.industrialhub.backend.qms;

import com.industrialhub.backend.qms.application.usecase.CompleteCorrectiveActionUseCase;
import com.industrialhub.backend.qms.application.usecase.CreateCorrectiveActionUseCase;
import com.industrialhub.backend.qms.application.usecase.CreateNcUseCase;
import com.industrialhub.backend.qms.application.usecase.CreateRcaUseCase;
import com.industrialhub.backend.qms.application.usecase.DeleteCorrectiveActionUseCase;
import com.industrialhub.backend.qms.application.usecase.ExportNcCsvUseCase;
import com.industrialhub.backend.qms.application.usecase.GetNcDetailUseCase;
import com.industrialhub.backend.qms.application.usecase.GetNcKpiSummaryUseCase;
import com.industrialhub.backend.qms.application.usecase.GetNcListUseCase;
import com.industrialhub.backend.qms.application.usecase.GetRcaByNcUseCase;
import com.industrialhub.backend.qms.application.usecase.ListCorrectiveActionsUseCase;
import com.industrialhub.backend.qms.application.usecase.SubmitForEffectivenessUseCase;
import com.industrialhub.backend.qms.application.usecase.TransitionNcStatusUseCase;
import com.industrialhub.backend.qms.application.usecase.UpdateCAPAUseCase;
import com.industrialhub.backend.qms.application.usecase.UpdateRcaUseCase;
import com.industrialhub.backend.qms.application.usecase.VerifyEffectivenessUseCase;
import com.industrialhub.backend.qms.presentation.QmsController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

/**
 * SEC-088 AC#3: GET /api/v1/qms/non-conformances deve retornar Cache-Control: no-store.
 */
@ExtendWith(MockitoExtension.class)
class NcListCacheControlTest {

    @Mock private CreateNcUseCase createNc;
    @Mock private TransitionNcStatusUseCase transitionStatus;
    @Mock private GetNcListUseCase getNcList;
    @Mock private GetNcDetailUseCase getNcDetail;
    @Mock private GetNcKpiSummaryUseCase getKpiSummary;
    @Mock private ExportNcCsvUseCase exportCsv;
    @Mock private CreateCorrectiveActionUseCase createAction;
    @Mock private ListCorrectiveActionsUseCase listActions;
    @Mock private CompleteCorrectiveActionUseCase completeAction;
    @Mock private DeleteCorrectiveActionUseCase deleteAction;
    @Mock private CreateRcaUseCase createRca;
    @Mock private UpdateRcaUseCase updateRca;
    @Mock private GetRcaByNcUseCase getRca;
    @Mock private UpdateCAPAUseCase updateCapa;
    @Mock private SubmitForEffectivenessUseCase submitForEffectiveness;
    @Mock private VerifyEffectivenessUseCase verifyEffectiveness;

    private QmsController controller;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        controller = new QmsController(
                createNc, transitionStatus, getNcList, getNcDetail, getKpiSummary,
                exportCsv, createAction, listActions, completeAction, deleteAction,
                createRca, updateRca, getRca, updateCapa, submitForEffectiveness, verifyEffectiveness);
        response = new MockHttpServletResponse();
    }

    @Test
    void list_shouldSetCacheControlNoStore() {
        Page<Object> emptyPage = new PageImpl<>(List.of());
        when(getNcList.execute(isNull(), isNull(), isNull(), isNull(), any(Pageable.class)))
                .thenReturn((Page) emptyPage);

        controller.list(null, null, null, null, Pageable.unpaged(), response);

        assertThat(response.getHeader("Cache-Control")).contains("no-store");
    }
}
