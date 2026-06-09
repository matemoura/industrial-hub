package com.industrialhub.backend.qms.complaints.application.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record LinkCapaRequest(@NotNull UUID capaId) {}
