package com.industrialhub.backend.common.presentation;

import com.industrialhub.backend.common.application.dto.SaveDashboardConfigRequest;
import com.industrialhub.backend.common.application.dto.UserDashboardConfigResponse;
import com.industrialhub.backend.common.application.usecase.DeleteDashboardConfigUseCase;
import com.industrialhub.backend.common.application.usecase.GetDashboardConfigUseCase;
import com.industrialhub.backend.common.application.usecase.SaveDashboardConfigUseCase;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users/me/dashboard")
@Validated
public class DashboardController {

    private final GetDashboardConfigUseCase getConfig;
    private final SaveDashboardConfigUseCase saveConfig;
    private final DeleteDashboardConfigUseCase deleteConfig;

    public DashboardController(GetDashboardConfigUseCase getConfig,
                               SaveDashboardConfigUseCase saveConfig,
                               DeleteDashboardConfigUseCase deleteConfig) {
        this.getConfig = getConfig;
        this.saveConfig = saveConfig;
        this.deleteConfig = deleteConfig;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public UserDashboardConfigResponse get(Authentication authentication) {
        String username = authentication.getName();
        // SEC-103: use anyMatch for consistent role derivation (same pattern as other controllers)
        boolean isSupervisorOrAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_SUPERVISOR") || a.getAuthority().equals("ROLE_ADMIN"));
        String role = isSupervisorOrAdmin ? "SUPERVISOR" : "OPERATOR";
        return getConfig.execute(username, role);
    }

    @PutMapping
    @PreAuthorize("isAuthenticated()")
    public UserDashboardConfigResponse save(
            Authentication authentication,
            @Valid @RequestBody SaveDashboardConfigRequest request) {
        return saveConfig.execute(authentication.getName(), request);
    }

    @DeleteMapping
    @PreAuthorize("isAuthenticated()")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(Authentication authentication) {
        deleteConfig.execute(authentication.getName());
    }
}
