package com.industrialhub.backend.qms.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateRcaRequest(
    @NotBlank(message = "why1 é obrigatório")
    @Size(max = 500, message = "why1 deve ter no máximo 500 caracteres")
    String why1,

    @NotBlank(message = "answer1 é obrigatório")
    @Size(max = 500, message = "answer1 deve ter no máximo 500 caracteres")
    String answer1,

    @Size(max = 500, message = "why2 deve ter no máximo 500 caracteres")
    String why2,

    @Size(max = 500, message = "answer2 deve ter no máximo 500 caracteres")
    String answer2,

    @Size(max = 500, message = "why3 deve ter no máximo 500 caracteres")
    String why3,

    @Size(max = 500, message = "answer3 deve ter no máximo 500 caracteres")
    String answer3,

    @Size(max = 500, message = "why4 deve ter no máximo 500 caracteres")
    String why4,

    @Size(max = 500, message = "answer4 deve ter no máximo 500 caracteres")
    String answer4,

    @Size(max = 500, message = "why5 deve ter no máximo 500 caracteres")
    String why5,

    @Size(max = 500, message = "answer5 deve ter no máximo 500 caracteres")
    String answer5,

    @Size(max = 1000, message = "rootCause deve ter no máximo 1000 caracteres")
    String rootCause
) {}
