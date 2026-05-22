package com.industrialhub.backend.common.application.dto;

import jakarta.validation.constraints.NotBlank;

public record AnonymizeUserRequest(
    @NotBlank String reason
) {}
