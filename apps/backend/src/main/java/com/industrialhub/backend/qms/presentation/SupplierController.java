package com.industrialhub.backend.qms.presentation;

import com.industrialhub.backend.qms.application.dto.CreateSupplierRequest;
import com.industrialhub.backend.qms.application.dto.SupplierQualityScore;
import com.industrialhub.backend.qms.application.dto.SupplierResponse;
import com.industrialhub.backend.qms.application.usecase.CreateSupplierUseCase;
import com.industrialhub.backend.qms.application.usecase.DeactivateSupplierUseCase;
import com.industrialhub.backend.qms.application.usecase.GetSupplierDetailUseCase;
import com.industrialhub.backend.qms.application.usecase.GetSupplierListUseCase;
import com.industrialhub.backend.qms.application.usecase.GetSupplierQualityUseCase;
import com.industrialhub.backend.qms.application.usecase.UpdateSupplierUseCase;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/v1/qms/suppliers")
public class SupplierController {

    private final CreateSupplierUseCase create;
    private final GetSupplierListUseCase list;
    private final GetSupplierDetailUseCase detail;
    private final UpdateSupplierUseCase update;
    private final DeactivateSupplierUseCase deactivate;
    private final GetSupplierQualityUseCase quality;

    public SupplierController(CreateSupplierUseCase create,
                              GetSupplierListUseCase list,
                              GetSupplierDetailUseCase detail,
                              UpdateSupplierUseCase update,
                              DeactivateSupplierUseCase deactivate,
                              GetSupplierQualityUseCase quality) {
        this.create = create;
        this.list = list;
        this.detail = detail;
        this.update = update;
        this.deactivate = deactivate;
        this.quality = quality;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public SupplierResponse create(@Valid @RequestBody CreateSupplierRequest request, Principal principal) {
        return create.execute(request, principal.getName());
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('OPERATOR', 'SUPERVISOR', 'ADMIN')")
    public List<SupplierResponse> list() {
        return list.execute();
    }

    @GetMapping("/quality-ranking")
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'ADMIN')")
    public List<SupplierQualityScore> ranking(@RequestParam(defaultValue = "90") @Min(1) @Max(730) int days) {
        return quality.executeRanking(days);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('OPERATOR', 'SUPERVISOR', 'ADMIN')")
    public SupplierResponse getById(@PathVariable UUID id) {
        return detail.execute(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public SupplierResponse update(@PathVariable UUID id, @Valid @RequestBody CreateSupplierRequest request,
                                   Principal principal) {
        return update.execute(id, request, principal.getName());
    }

    @PutMapping("/{id}/deactivate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void deactivate(@PathVariable UUID id, Principal principal) {
        deactivate.execute(id, principal.getName());
    }

    @GetMapping("/{id}/quality-score")
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'ADMIN')")
    public SupplierQualityScore qualityScore(@PathVariable UUID id,
                                             @RequestParam(defaultValue = "90") @Min(1) @Max(730) int days) {
        return quality.executeForSupplier(id, days);
    }
}
