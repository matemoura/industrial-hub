package com.industrialhub.backend.common;

import com.industrialhub.backend.oee.application.usecase.DuplicateImportException;
import com.industrialhub.backend.oee.application.usecase.InvalidExcelFormatException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(DuplicateImportException.class)
    public ResponseEntity<Map<String, Object>> handleDuplicateImport(DuplicateImportException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "message", ex.getMessage(),
                "existingBatchId", ex.getExistingBatchId(),
                "periodDate", ex.getPeriodDate().toString(),
                "timestamp", Instant.now().toString()
        ));
    }

    @ExceptionHandler(InvalidExcelFormatException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidFormat(InvalidExcelFormatException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of(
                "message", ex.getMessage(),
                "timestamp", Instant.now().toString()
        ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex) {
        log.error("Erro não tratado", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "message", "Erro interno. Contate o administrador.",
                "timestamp", Instant.now().toString()
        ));
    }
}
