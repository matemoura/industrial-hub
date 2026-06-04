package com.industrialhub.backend.qms.application.dto;

import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * Sprint 39 / US-116: body para atualizar dueDate de uma CAPA.
 */
public record UpdateCapaDueDateRequest(
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate dueDate
) {}
