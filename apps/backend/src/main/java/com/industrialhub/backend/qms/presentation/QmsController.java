package com.industrialhub.backend.qms.presentation;

import com.industrialhub.backend.qms.application.dto.ActionResponse;
import com.industrialhub.backend.qms.application.dto.CreateActionRequest;
import com.industrialhub.backend.qms.application.dto.CreateNcRequest;
import com.industrialhub.backend.qms.application.dto.CreateRcaRequest;
import com.industrialhub.backend.qms.application.dto.NcKpiSummary;
import com.industrialhub.backend.qms.application.dto.NcResponse;
import com.industrialhub.backend.qms.application.dto.NcSummaryItem;
import com.industrialhub.backend.qms.application.dto.RcaResponse;
import com.industrialhub.backend.qms.application.dto.TransitionStatusRequest;
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
import com.industrialhub.backend.qms.application.usecase.TransitionNcStatusUseCase;
import com.industrialhub.backend.qms.application.usecase.UpdateRcaUseCase;
import com.industrialhub.backend.qms.domain.NcSeverity;
import com.industrialhub.backend.qms.domain.NcStatus;
import com.industrialhub.backend.qms.domain.NcType;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.security.Principal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/qms/non-conformances")
public class QmsController {

    private final CreateNcUseCase createNc;
    private final TransitionNcStatusUseCase transitionStatus;
    private final GetNcListUseCase getNcList;
    private final GetNcDetailUseCase getNcDetail;
    private final GetNcKpiSummaryUseCase getKpiSummary;
    private final ExportNcCsvUseCase exportCsv;
    private final CreateCorrectiveActionUseCase createAction;
    private final ListCorrectiveActionsUseCase listActions;
    private final CompleteCorrectiveActionUseCase completeAction;
    private final DeleteCorrectiveActionUseCase deleteAction;
    private final CreateRcaUseCase createRca;
    private final UpdateRcaUseCase updateRca;
    private final GetRcaByNcUseCase getRca;

    public QmsController(CreateNcUseCase createNc,
                         TransitionNcStatusUseCase transitionStatus,
                         GetNcListUseCase getNcList,
                         GetNcDetailUseCase getNcDetail,
                         GetNcKpiSummaryUseCase getKpiSummary,
                         ExportNcCsvUseCase exportCsv,
                         CreateCorrectiveActionUseCase createAction,
                         ListCorrectiveActionsUseCase listActions,
                         CompleteCorrectiveActionUseCase completeAction,
                         DeleteCorrectiveActionUseCase deleteAction,
                         CreateRcaUseCase createRca,
                         UpdateRcaUseCase updateRca,
                         GetRcaByNcUseCase getRca) {
        this.createNc = createNc;
        this.transitionStatus = transitionStatus;
        this.getNcList = getNcList;
        this.getNcDetail = getNcDetail;
        this.getKpiSummary = getKpiSummary;
        this.exportCsv = exportCsv;
        this.createAction = createAction;
        this.listActions = listActions;
        this.completeAction = completeAction;
        this.deleteAction = deleteAction;
        this.createRca = createRca;
        this.updateRca = updateRca;
        this.getRca = getRca;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('OPERATOR', 'SUPERVISOR', 'ADMIN')")
    public NcResponse create(@Valid @RequestBody CreateNcRequest request, Principal principal) {
        return createNc.execute(request, principal.getName());
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('OPERATOR', 'SUPERVISOR', 'ADMIN')")
    public Page<NcSummaryItem> list(
            @RequestParam(required = false) NcStatus status,
            @RequestParam(required = false) NcSeverity severity,
            @RequestParam(required = false) NcType type,
            @RequestParam(required = false) Boolean slaBreached,
            @PageableDefault(size = 20, sort = "reportedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return getNcList.execute(status, severity, type, slaBreached, pageable);
    }

    @GetMapping("/summary")
    @PreAuthorize("hasAnyRole('OPERATOR', 'SUPERVISOR', 'ADMIN')")
    public NcKpiSummary getSummary() {
        return getKpiSummary.execute();
    }

    @GetMapping("/export")
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'ADMIN')")
    public void export(HttpServletResponse response) throws IOException {
        response.setContentType("text/csv; charset=utf-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"ncs-export.csv\"");
        exportCsv.export(response.getWriter());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('OPERATOR', 'SUPERVISOR', 'ADMIN')")
    public NcResponse getById(@PathVariable UUID id) {
        return getNcDetail.execute(id);
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'ADMIN')")
    public NcResponse transition(@PathVariable UUID id,
                                  @Valid @RequestBody TransitionStatusRequest request,
                                  Principal principal) {
        return transitionStatus.execute(id, request.status(), principal.getName());
    }

    @PostMapping("/{id}/actions")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'ADMIN')")
    public ActionResponse createAction(@PathVariable UUID id,
                                       @Valid @RequestBody CreateActionRequest request,
                                       Principal principal) {
        return createAction.execute(id, request, principal.getName());
    }

    @GetMapping("/{id}/actions")
    @PreAuthorize("hasAnyRole('OPERATOR', 'SUPERVISOR', 'ADMIN')")
    public List<ActionResponse> listActions(@PathVariable UUID id) {
        return listActions.execute(id);
    }

    @PutMapping("/{id}/actions/{aid}/complete")
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'ADMIN')")
    public ActionResponse completeAction(@PathVariable UUID id,
                                          @PathVariable UUID aid,
                                          Principal principal) {
        return completeAction.execute(id, aid, principal.getName());
    }

    @DeleteMapping("/{id}/actions/{aid}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'ADMIN')")
    public void deleteAction(@PathVariable UUID id, @PathVariable UUID aid) {
        deleteAction.execute(id, aid);
    }

    @PostMapping("/{ncId}/rca")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'ADMIN')")
    public RcaResponse createRca(@PathVariable UUID ncId,
                                 @Valid @RequestBody CreateRcaRequest request,
                                 Principal principal) {
        return createRca.execute(ncId, request, principal.getName());
    }

    @GetMapping("/{ncId}/rca")
    @PreAuthorize("hasAnyRole('OPERATOR', 'SUPERVISOR', 'ADMIN')")
    public RcaResponse getRca(@PathVariable UUID ncId) {
        return getRca.execute(ncId);
    }

    @PutMapping("/{ncId}/rca")
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'ADMIN')")
    public RcaResponse updateRca(@PathVariable UUID ncId,
                                 @Valid @RequestBody CreateRcaRequest request,
                                 Principal principal) {
        return updateRca.execute(ncId, request, principal.getName());
    }
}
