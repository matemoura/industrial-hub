package com.industrialhub.backend.common.auth.application.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record UpdateUserPermissionsRequest(
        @NotNull @Valid List<UserModulePermissionResponse> permissions
) {}
