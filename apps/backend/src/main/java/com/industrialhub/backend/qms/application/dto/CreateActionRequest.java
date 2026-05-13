package com.industrialhub.backend.qms.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record CreateActionRequest(
    @NotBlank(message = "Descrição é obrigatória")
    @Size(max = 1000, message = "Descrição deve ter no máximo 1000 caracteres")
    String description,

    @NotBlank(message = "Responsável é obrigatório")
    @Size(max = 50, message = "Responsável deve ter no máximo 50 caracteres")
    String responsible,

    @NotNull(message = "Data de vencimento é obrigatória")
    LocalDate dueDate
) {}
