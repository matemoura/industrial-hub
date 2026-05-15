package com.industrialhub.backend.common.auth.application.dto;

import com.industrialhub.backend.common.auth.domain.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateUserRequest(
        @NotBlank String username,
        @NotBlank @Email String email,
        @NotNull Role role,
        @NotBlank String temporaryPassword
) {}
