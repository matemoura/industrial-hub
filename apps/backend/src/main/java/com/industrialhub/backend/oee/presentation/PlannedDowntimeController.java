package com.industrialhub.backend.oee.presentation;

import com.industrialhub.backend.oee.application.dto.CreatePlannedDowntimeRequest;
import com.industrialhub.backend.oee.application.dto.PlannedDowntimeResponse;
import com.industrialhub.backend.oee.application.dto.UpdatePlannedDowntimeRequest;
import com.industrialhub.backend.oee.application.usecase.CreatePlannedDowntimeUseCase;
import com.industrialhub.backend.oee.application.usecase.DeletePlannedDowntimeUseCase;
import com.industrialhub.backend.oee.application.usecase.GetPlannedDowntimesUseCase;
import com.industrialhub.backend.oee.application.usecase.UpdatePlannedDowntimeUseCase;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/oee/planned-downtimes")
public class PlannedDowntimeController {

    private final CreatePlannedDowntimeUseCase createUseCase;
    private final GetPlannedDowntimesUseCase getUseCase;
    private final UpdatePlannedDowntimeUseCase updateUseCase;
    private final DeletePlannedDowntimeUseCase deleteUseCase;

    public PlannedDowntimeController(CreatePlannedDowntimeUseCase createUseCase,
                                     GetPlannedDowntimesUseCase getUseCase,
                                     UpdatePlannedDowntimeUseCase updateUseCase,
                                     DeletePlannedDowntimeUseCase deleteUseCase) {
        this.createUseCase = createUseCase;
        this.getUseCase = getUseCase;
        this.updateUseCase = updateUseCase;
        this.deleteUseCase = deleteUseCase;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPERVISOR','ADMIN')")
    public ResponseEntity<PlannedDowntimeResponse> create(
            @Valid @RequestBody CreatePlannedDowntimeRequest request,
            Principal principal) {
        PlannedDowntimeResponse response = createUseCase.execute(request,
                principal != null ? principal.getName() : "system");
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('OPERATOR','SUPERVISOR','ADMIN')")
    public ResponseEntity<List<PlannedDowntimeResponse>> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) UUID equipmentId) {
        return ResponseEntity.ok(getUseCase.execute(date, equipmentId));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPERVISOR','ADMIN')")
    public ResponseEntity<PlannedDowntimeResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdatePlannedDowntimeRequest request,
            Principal principal) {
        return ResponseEntity.ok(updateUseCase.execute(id, request,
                principal != null ? principal.getName() : "system"));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPERVISOR','ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable UUID id, Principal principal) {
        deleteUseCase.execute(id, principal != null ? principal.getName() : "system");
        return ResponseEntity.noContent().build();
    }
}
