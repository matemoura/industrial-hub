package com.industrialhub.backend.common.presentation;

import com.industrialhub.backend.common.application.dto.CreateShiftRequest;
import com.industrialhub.backend.common.application.dto.ShiftResponse;
import com.industrialhub.backend.common.application.dto.UpdateShiftRequest;
import com.industrialhub.backend.common.application.usecase.CreateShiftUseCase;
import com.industrialhub.backend.common.application.usecase.DeactivateShiftUseCase;
import com.industrialhub.backend.common.application.usecase.GetShiftListUseCase;
import com.industrialhub.backend.common.application.usecase.UpdateShiftUseCase;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/shifts")
@Validated
public class ShiftController {

    private final CreateShiftUseCase createShift;
    private final GetShiftListUseCase getShiftList;
    private final UpdateShiftUseCase updateShift;
    private final DeactivateShiftUseCase deactivateShift;

    public ShiftController(CreateShiftUseCase createShift,
                           GetShiftListUseCase getShiftList,
                           UpdateShiftUseCase updateShift,
                           DeactivateShiftUseCase deactivateShift) {
        this.createShift = createShift;
        this.getShiftList = getShiftList;
        this.updateShift = updateShift;
        this.deactivateShift = deactivateShift;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ShiftResponse> create(@Valid @RequestBody CreateShiftRequest request) {
        ShiftResponse response = createShift.execute(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('OPERATOR', 'SUPERVISOR', 'ADMIN')")
    public ResponseEntity<List<ShiftResponse>> list() {
        return ResponseEntity.ok(getShiftList.execute());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ShiftResponse> update(@PathVariable UUID id,
                                                 @Valid @RequestBody UpdateShiftRequest request) {
        return ResponseEntity.ok(updateShift.execute(id, request));
    }

    @PutMapping("/{id}/deactivate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void deactivate(@PathVariable UUID id) {
        deactivateShift.execute(id);
    }
}
