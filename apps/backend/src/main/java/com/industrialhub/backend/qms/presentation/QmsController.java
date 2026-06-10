package com.industrialhub.backend.qms.presentation;

import com.industrialhub.backend.qms.application.dto.ActionResponse;
import com.industrialhub.backend.qms.application.dto.CAPAUpdateRequest;
import com.industrialhub.backend.qms.application.dto.CreateActionRequest;
import com.industrialhub.backend.qms.application.dto.CreateNcRequest;
import com.industrialhub.backend.qms.application.dto.CreateRcaRequest;
import com.industrialhub.backend.qms.application.dto.LinkNcToDocumentRequest;
import com.industrialhub.backend.qms.application.dto.NcDocumentLinkResponse;
import com.industrialhub.backend.qms.application.dto.NcKpiSummary;
import com.industrialhub.backend.qms.application.dto.NcResponse;
import com.industrialhub.backend.qms.application.dto.NcSummaryItem;
import com.industrialhub.backend.qms.application.dto.RcaResponse;
import com.industrialhub.backend.qms.application.dto.TransitionStatusRequest;
import com.industrialhub.backend.qms.application.dto.VerifyEffectivenessRequest;
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
import com.industrialhub.backend.qms.application.usecase.LinkNcToDocumentUseCase;
import com.industrialhub.backend.qms.application.usecase.ListCorrectiveActionsUseCase;
import com.industrialhub.backend.qms.application.usecase.ListNcDocumentLinksUseCase;
import com.industrialhub.backend.qms.application.usecase.SubmitForEffectivenessUseCase;
import com.industrialhub.backend.qms.application.usecase.TransitionNcStatusUseCase;
import com.industrialhub.backend.qms.application.usecase.UnlinkNcFromDocumentUseCase;
import com.industrialhub.backend.qms.application.usecase.UpdateCAPAUseCase;
import com.industrialhub.backend.qms.application.usecase.UpdateRcaUseCase;
import com.industrialhub.backend.qms.application.usecase.VerifyEffectivenessUseCase;
import com.industrialhub.backend.qms.domain.NcSeverity;
import com.industrialhub.backend.qms.domain.NcStatus;
import com.industrialhub.backend.qms.domain.NcType;
import com.industrialhub.backend.qms.risk.application.dto.RiskItemSummary;
import com.industrialhub.backend.qms.risk.application.usecase.GetRisksByNcUseCase;
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
    private final UpdateCAPAUseCase updateCapa;
    private final SubmitForEffectivenessUseCase submitForEffectiveness;
    private final VerifyEffectivenessUseCase verifyEffectiveness;
    private final LinkNcToDocumentUseCase linkNcToDocument;
    private final ListNcDocumentLinksUseCase listNcDocumentLinks;
    private final UnlinkNcFromDocumentUseCase unlinkNcFromDocument;
    private final GetRisksByNcUseCase getRisksByNc;

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
                         GetRcaByNcUseCase getRca,
                         UpdateCAPAUseCase updateCapa,
                         SubmitForEffectivenessUseCase submitForEffectiveness,
                         VerifyEffectivenessUseCase verifyEffectiveness,
                         LinkNcToDocumentUseCase linkNcToDocument,
                         ListNcDocumentLinksUseCase listNcDocumentLinks,
                         UnlinkNcFromDocumentUseCase unlinkNcFromDocument,
                         GetRisksByNcUseCase getRisksByNc) {
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
        this.updateCapa = updateCapa;
        this.submitForEffectiveness = submitForEffectiveness;
        this.verifyEffectiveness = verifyEffectiveness;
        this.linkNcToDocument = linkNcToDocument;
        this.listNcDocumentLinks = listNcDocumentLinks;
        this.unlinkNcFromDocument = unlinkNcFromDocument;
        this.getRisksByNc = getRisksByNc;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@perm.canCreate(authentication.name, T(com.industrialhub.backend.common.auth.domain.AppModule).QMS)")
    public NcResponse create(@Valid @RequestBody CreateNcRequest request, Principal principal) {
        return createNc.execute(request, principal.getName());
    }

    @GetMapping
    @PreAuthorize("@perm.canView(authentication.name, T(com.industrialhub.backend.common.auth.domain.AppModule).QMS)")
    public Page<NcSummaryItem> list(
            @RequestParam(required = false) NcStatus status,
            @RequestParam(required = false) NcSeverity severity,
            @RequestParam(required = false) NcType type,
            @RequestParam(required = false) Boolean slaBreached,
            @PageableDefault(size = 20, sort = "reportedAt", direction = Sort.Direction.DESC) Pageable pageable,
            HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        return getNcList.execute(status, severity, type, slaBreached, pageable);
    }

    @GetMapping("/summary")
    @PreAuthorize("@perm.canView(authentication.name, T(com.industrialhub.backend.common.auth.domain.AppModule).QMS)")
    public NcKpiSummary getSummary() {
        return getKpiSummary.execute();
    }

    @GetMapping("/export")
    @PreAuthorize("@perm.canView(authentication.name, T(com.industrialhub.backend.common.auth.domain.AppModule).QMS)")
    public void export(HttpServletResponse response) throws IOException {
        response.setContentType("text/csv; charset=utf-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"ncs-export.csv\"");
        exportCsv.export(response.getWriter());
    }

    @GetMapping("/{id}")
    @PreAuthorize("@perm.canView(authentication.name, T(com.industrialhub.backend.common.auth.domain.AppModule).QMS)")
    public NcResponse getById(@PathVariable UUID id) {
        return getNcDetail.execute(id);
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("@perm.canEdit(authentication.name, T(com.industrialhub.backend.common.auth.domain.AppModule).QMS)")
    public NcResponse transition(@PathVariable UUID id,
                                  @Valid @RequestBody TransitionStatusRequest request,
                                  Principal principal) {
        return transitionStatus.execute(id, request.status(), principal.getName());
    }

    @PostMapping("/{id}/actions")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@perm.canCreate(authentication.name, T(com.industrialhub.backend.common.auth.domain.AppModule).QMS)")
    public ActionResponse createAction(@PathVariable UUID id,
                                       @Valid @RequestBody CreateActionRequest request,
                                       Principal principal) {
        return createAction.execute(id, request, principal.getName());
    }

    @GetMapping("/{id}/actions")
    @PreAuthorize("@perm.canView(authentication.name, T(com.industrialhub.backend.common.auth.domain.AppModule).QMS)")
    public List<ActionResponse> listActions(@PathVariable UUID id) {
        return listActions.execute(id);
    }

    @PutMapping("/{id}/actions/{aid}/complete")
    @PreAuthorize("@perm.canEdit(authentication.name, T(com.industrialhub.backend.common.auth.domain.AppModule).QMS)")
    public ActionResponse completeAction(@PathVariable UUID id,
                                          @PathVariable UUID aid,
                                          Principal principal) {
        return completeAction.execute(id, aid, principal.getName());
    }

    @DeleteMapping("/{id}/actions/{aid}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@perm.canDelete(authentication.name, T(com.industrialhub.backend.common.auth.domain.AppModule).QMS)")
    public void deleteAction(@PathVariable UUID id, @PathVariable UUID aid) {
        deleteAction.execute(id, aid);
    }

    @PutMapping("/{ncId}/corrective-actions/{actionId}")
    @PreAuthorize("@perm.canEdit(authentication.name, T(com.industrialhub.backend.common.auth.domain.AppModule).QMS)")
    public ActionResponse updateCapa(@PathVariable UUID ncId,
                                      @PathVariable UUID actionId,
                                      @RequestBody @Valid CAPAUpdateRequest req) {
        return updateCapa.execute(ncId, actionId, req);
    }

    @PostMapping("/{id}/actions/{actionId}/submit-for-effectiveness")
    @PreAuthorize("@perm.canEdit(authentication.name, T(com.industrialhub.backend.common.auth.domain.AppModule).QMS)")
    public ActionResponse submitForEffectiveness(@PathVariable UUID id,
                                                  @PathVariable UUID actionId,
                                                  Principal principal) {
        return submitForEffectiveness.execute(id, actionId, principal.getName());
    }

    @PostMapping("/{id}/actions/{actionId}/verify-effectiveness")
    @PreAuthorize("@perm.canEdit(authentication.name, T(com.industrialhub.backend.common.auth.domain.AppModule).QMS)")
    public ActionResponse verifyEffectiveness(@PathVariable UUID id,
                                               @PathVariable UUID actionId,
                                               @RequestBody @Valid VerifyEffectivenessRequest req,
                                               Principal principal) {
        return verifyEffectiveness.execute(id, actionId, req, principal.getName());
    }

    @PostMapping("/{ncId}/rca")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@perm.canCreate(authentication.name, T(com.industrialhub.backend.common.auth.domain.AppModule).QMS)")
    public RcaResponse createRca(@PathVariable UUID ncId,
                                 @Valid @RequestBody CreateRcaRequest request,
                                 Principal principal) {
        return createRca.execute(ncId, request, principal.getName());
    }

    @GetMapping("/{ncId}/rca")
    @PreAuthorize("@perm.canView(authentication.name, T(com.industrialhub.backend.common.auth.domain.AppModule).QMS)")
    public RcaResponse getRca(@PathVariable UUID ncId) {
        return getRca.execute(ncId);
    }

    @PutMapping("/{ncId}/rca")
    @PreAuthorize("@perm.canEdit(authentication.name, T(com.industrialhub.backend.common.auth.domain.AppModule).QMS)")
    public RcaResponse updateRca(@PathVariable UUID ncId,
                                 @Valid @RequestBody CreateRcaRequest request,
                                 Principal principal) {
        return updateRca.execute(ncId, request, principal.getName());
    }

    // Sprint 39 — US-115: NC↔GED Link endpoints

    @PostMapping("/{ncId}/documents")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@perm.canCreate(authentication.name, T(com.industrialhub.backend.common.auth.domain.AppModule).QMS)")
    public NcDocumentLinkResponse linkDocument(@PathVariable UUID ncId,
                                               @Valid @RequestBody LinkNcToDocumentRequest req,
                                               Principal principal) {
        return linkNcToDocument.execute(ncId, req, principal.getName());
    }

    @GetMapping("/{ncId}/documents")
    @PreAuthorize("@perm.canView(authentication.name, T(com.industrialhub.backend.common.auth.domain.AppModule).QMS)")
    public List<NcDocumentLinkResponse> listDocumentLinks(@PathVariable UUID ncId) {
        return listNcDocumentLinks.execute(ncId);
    }

    @DeleteMapping("/{ncId}/documents/{documentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@perm.canDelete(authentication.name, T(com.industrialhub.backend.common.auth.domain.AppModule).QMS)")
    public void unlinkDocument(@PathVariable UUID ncId, @PathVariable UUID documentId,
                               java.security.Principal principal) {
        unlinkNcFromDocument.execute(ncId, documentId, principal.getName());
    }

    // Sprint 43 — US-128: NC-Risco rastreabilidade
    @GetMapping("/{ncId}/risks")
    @PreAuthorize("@perm.canView(authentication.name, T(com.industrialhub.backend.common.auth.domain.AppModule).QMS)")
    public List<RiskItemSummary> getRisksByNc(@PathVariable UUID ncId) {
        return getRisksByNc.execute(ncId);
    }
}
