package com.industrialhub.backend.qms.presentation;

import com.industrialhub.backend.qms.application.dto.CAPASummaryResponse;
import com.industrialhub.backend.qms.application.dto.CapaAgingResponse;
import com.industrialhub.backend.qms.application.dto.UpdateCapaDueDateRequest;
import com.industrialhub.backend.qms.application.usecase.ExportCapaAgingCsvUseCase;
import com.industrialhub.backend.qms.application.usecase.GetCapaAgingUseCase;
import com.industrialhub.backend.qms.application.usecase.ListCAPAsUseCase;
import com.industrialhub.backend.qms.application.usecase.UpdateCapaDueDateUseCase;
import com.industrialhub.backend.qms.domain.ActionStatus;
import com.industrialhub.backend.qms.domain.ActionType;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/qms/capas")
@Validated
public class CapaController {

    private final ListCAPAsUseCase listCAPAs;
    private final GetCapaAgingUseCase getCapaAging;
    private final ExportCapaAgingCsvUseCase exportCapaAgingCsv;
    private final UpdateCapaDueDateUseCase updateCapaDueDate;

    public CapaController(ListCAPAsUseCase listCAPAs,
                          GetCapaAgingUseCase getCapaAging,
                          ExportCapaAgingCsvUseCase exportCapaAgingCsv,
                          UpdateCapaDueDateUseCase updateCapaDueDate) {
        this.listCAPAs = listCAPAs;
        this.getCapaAging = getCapaAging;
        this.exportCapaAgingCsv = exportCapaAgingCsv;
        this.updateCapaDueDate = updateCapaDueDate;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'ADMIN')")
    public Page<CAPASummaryResponse> listCapas(
            @RequestParam(required = false) ActionType type,
            @RequestParam(required = false) ActionStatus status,
            @RequestParam(required = false) UUID ncId,
            @PageableDefault(size = 20) Pageable pageable) {
        return listCAPAs.execute(type, status, ncId, pageable);
    }

    /**
     * Sprint 39 / US-116: dashboard de aging de CAPAs.
     */
    @GetMapping("/aging")
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'ADMIN')")
    public CapaAgingResponse getAging() {
        return getCapaAging.execute();
    }

    /**
     * Sprint 39 / US-116: exporta CAPAs abertas como CSV (UTF-8 BOM + ';').
     */
    @GetMapping("/aging/export")
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'ADMIN')")
    public ResponseEntity<byte[]> exportAgingCsv() {
        byte[] csv = exportCapaAgingCsv.execute();
        String filename = "capas-aging-" + LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE) + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "text/csv; charset=utf-8")
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(csv);
    }

    /**
     * Sprint 39 / US-116: atualiza dueDate de uma CAPA.
     */
    @PutMapping("/{id}/due-date")
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'ADMIN')")
    public CAPASummaryResponse updateDueDate(@PathVariable UUID id,
                                              @RequestBody @Valid UpdateCapaDueDateRequest req) {
        return updateCapaDueDate.execute(id, req);
    }
}
