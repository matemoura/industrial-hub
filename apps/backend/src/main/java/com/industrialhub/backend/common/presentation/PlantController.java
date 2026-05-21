package com.industrialhub.backend.common.presentation;

import com.industrialhub.backend.common.application.dto.AssignUserPlantsRequest;
import com.industrialhub.backend.common.application.dto.CreatePlantRequest;
import com.industrialhub.backend.common.application.dto.PlantResponse;
import com.industrialhub.backend.common.application.dto.UpdatePlantRequest;
import com.industrialhub.backend.common.application.usecase.AssignUserPlantsUseCase;
import com.industrialhub.backend.common.application.usecase.CreatePlantUseCase;
import com.industrialhub.backend.common.application.usecase.DeactivatePlantUseCase;
import com.industrialhub.backend.common.application.usecase.GetPlantListUseCase;
import com.industrialhub.backend.common.application.usecase.UpdatePlantUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/plants")
@RequiredArgsConstructor
public class PlantController {

    private final CreatePlantUseCase createPlant;
    private final GetPlantListUseCase getPlantList;
    private final UpdatePlantUseCase updatePlant;
    private final DeactivatePlantUseCase deactivatePlant;
    private final AssignUserPlantsUseCase assignUserPlants;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<PlantResponse> list() {
        return getPlantList.execute();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public PlantResponse create(@Valid @RequestBody CreatePlantRequest request,
                                 Principal principal) {
        return createPlant.execute(request, principal.getName());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public PlantResponse update(@PathVariable UUID id,
                                 @Valid @RequestBody UpdatePlantRequest request,
                                 Principal principal) {
        return updatePlant.execute(id, request, principal.getName());
    }

    @PutMapping("/{id}/deactivate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void deactivate(@PathVariable UUID id, Principal principal) {
        deactivatePlant.execute(id, principal.getName());
    }

    @PutMapping("/users/{userId}/plants")
    @PreAuthorize("hasRole('ADMIN')")
    public List<PlantResponse> assignPlants(@PathVariable UUID userId,
                                             @Valid @RequestBody AssignUserPlantsRequest request,
                                             Principal principal) {
        return assignUserPlants.execute(userId, request, principal.getName());
    }
}
