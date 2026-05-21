package com.industrialhub.backend.common.presentation;

import com.industrialhub.backend.common.application.dto.AssignUserPlantsRequest;
import com.industrialhub.backend.common.application.dto.PlantResponse;
import com.industrialhub.backend.common.application.usecase.AssignUserPlantsUseCase;
import com.industrialhub.backend.common.application.usecase.ListUserPlantsUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

/**
 * Admin endpoints scoped to user management — aligned with
 * frontend expectation: /api/v1/admin/users/{userId}/plants
 */
@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final AssignUserPlantsUseCase assignUserPlants;
    private final ListUserPlantsUseCase listUserPlants;

    /**
     * GET /api/v1/admin/users/{userId}/plants
     * Returns all plants associated with the given user. AC#11.
     */
    @GetMapping("/{userId}/plants")
    @PreAuthorize("hasRole('ADMIN')")
    public List<PlantResponse> getUserPlants(@PathVariable UUID userId) {
        return listUserPlants.execute(userId);
    }

    /**
     * PUT /api/v1/admin/users/{userId}/plants
     * Replaces all plant associations for the given user. BUG-S23-01.
     */
    @PutMapping("/{userId}/plants")
    @PreAuthorize("hasRole('ADMIN')")
    public List<PlantResponse> assignPlants(@PathVariable UUID userId,
                                             @Valid @RequestBody AssignUserPlantsRequest request,
                                             Principal principal) {
        return assignUserPlants.execute(userId, request, principal.getName());
    }
}
