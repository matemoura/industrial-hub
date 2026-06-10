package com.industrialhub.backend.common.auth.application.dto;

public record LoginResponseDto(
        String token,
        String username,
        String role,
        String email,
        long expiresInMs,
        boolean mustChangePassword
) {}
