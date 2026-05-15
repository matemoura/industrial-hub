package com.industrialhub.backend.common.auth.application.dto;

import com.industrialhub.backend.common.auth.domain.Role;
import jakarta.validation.constraints.NotNull;

public record UpdateUserRoleRequest(@NotNull Role role) {}
