package com.industrialhub.backend.qms.presentation;

import com.industrialhub.backend.qms.application.dto.CreateNcRequest;
import com.industrialhub.backend.qms.application.dto.NcKpiSummary;
import com.industrialhub.backend.qms.application.dto.NcResponse;
import com.industrialhub.backend.qms.application.dto.NcSummaryItem;
import com.industrialhub.backend.qms.application.dto.TransitionStatusRequest;
import com.industrialhub.backend.qms.application.usecase.CreateNcUseCase;
import com.industrialhub.backend.qms.application.usecase.ExportNcCsvUseCase;
import com.industrialhub.backend.qms.application.usecase.GetNcDetailUseCase;
import com.industrialhub.backend.qms.application.usecase.GetNcKpiSummaryUseCase;
import com.industrialhub.backend.qms.application.usecase.GetNcListUseCase;
import com.industrialhub.backend.qms.application.usecase.TransitionNcStatusUseCase;
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

    public QmsController(CreateNcUseCase createNc,
                         TransitionNcStatusUseCase transitionStatus,
                         GetNcListUseCase getNcList,
                         GetNcDetailUseCase getNcDetail,
                         GetNcKpiSummaryUseCase getKpiSummary,
                         ExportNcCsvUseCase exportCsv) {
        this.createNc = createNc;
        this.transitionStatus = transitionStatus;
        this.getNcList = getNcList;
        this.getNcDetail = getNcDetail;
        this.getKpiSummary = getKpiSummary;
        this.exportCsv = exportCsv;
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
            @PageableDefault(size = 20, sort = "reportedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return getNcList.execute(status, severity, type, pageable);
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
}
