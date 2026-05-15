package com.industrialhub.backend.common;

import com.industrialhub.backend.common.auth.application.usecase.InvalidCredentialsException;
import com.industrialhub.backend.common.security.TooManyRequestsException;
import com.industrialhub.backend.maintenance.domain.EquipmentDuplicateCodeException;
import com.industrialhub.backend.maintenance.domain.EquipmentHasOpenOrdersException;
import com.industrialhub.backend.maintenance.domain.EquipmentNotFoundException;
import com.industrialhub.backend.maintenance.domain.InvalidWorkOrderTransitionException;
import com.industrialhub.backend.maintenance.domain.WorkOrderNotFoundException;
import com.industrialhub.backend.oee.application.usecase.DuplicateImportException;
import com.industrialhub.backend.oee.application.usecase.InvalidExcelFormatException;
import com.industrialhub.backend.oee.application.validation.InvalidDateRangeException;
import com.industrialhub.backend.qms.domain.ActionNotAllowedException;
import com.industrialhub.backend.qms.domain.ActionNotFoundException;
import com.industrialhub.backend.qms.domain.InvalidNcTransitionException;
import com.industrialhub.backend.qms.domain.NcNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getDefaultMessage() != null
                        ? fe.getDefaultMessage()
                        : fe.getField() + " inválido")
                .collect(Collectors.joining("; "));
        return ResponseEntity.badRequest().body(Map.of(
                "message", message.isBlank() ? "Dados inválidos" : message,
                "timestamp", Instant.now().toString()
        ));
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidCredentials(InvalidCredentialsException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                "message", ex.getMessage(),
                "timestamp", Instant.now().toString()
        ));
    }

    @ExceptionHandler(TooManyRequestsException.class)
    public ResponseEntity<Map<String, Object>> handleTooManyRequests(TooManyRequestsException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", String.valueOf(ex.getRetryAfterSeconds()))
                .body(Map.of(
                        "message", ex.getMessage(),
                        "timestamp", Instant.now().toString()
                ));
    }

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

    @ExceptionHandler(InvalidDateRangeException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidDateRange(InvalidDateRangeException ex) {
        return ResponseEntity.badRequest().body(Map.of(
                "message", ex.getMessage(),
                "timestamp", Instant.now().toString()
        ));
    }

    @ExceptionHandler(NcNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNcNotFound(NcNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "message", ex.getMessage(),
                "timestamp", Instant.now().toString()
        ));
    }

    @ExceptionHandler(InvalidNcTransitionException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidNcTransition(InvalidNcTransitionException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of(
                "message", ex.getMessage(),
                "allowedNext", ex.getAllowedNext(),
                "timestamp", Instant.now().toString()
        ));
    }

    @ExceptionHandler(ActionNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleActionNotFound(ActionNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "message", ex.getMessage(),
                "timestamp", Instant.now().toString()
        ));
    }

    @ExceptionHandler(ActionNotAllowedException.class)
    public ResponseEntity<Map<String, Object>> handleActionNotAllowed(ActionNotAllowedException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of(
                "message", ex.getMessage(),
                "timestamp", Instant.now().toString()
        ));
    }

    @ExceptionHandler(EquipmentNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleEquipmentNotFound(EquipmentNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "message", ex.getMessage(),
                "timestamp", Instant.now().toString()
        ));
    }

    @ExceptionHandler(EquipmentDuplicateCodeException.class)
    public ResponseEntity<Map<String, Object>> handleEquipmentDuplicateCode(EquipmentDuplicateCodeException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "message", ex.getMessage(),
                "timestamp", Instant.now().toString()
        ));
    }

    @ExceptionHandler(EquipmentHasOpenOrdersException.class)
    public ResponseEntity<Map<String, Object>> handleEquipmentHasOpenOrders(EquipmentHasOpenOrdersException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "message", ex.getMessage(),
                "timestamp", Instant.now().toString()
        ));
    }

    @ExceptionHandler(WorkOrderNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleWorkOrderNotFound(WorkOrderNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "message", ex.getMessage(),
                "timestamp", Instant.now().toString()
        ));
    }

    @ExceptionHandler(InvalidWorkOrderTransitionException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidWorkOrderTransition(InvalidWorkOrderTransitionException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of(
                "message", ex.getMessage(),
                "allowedNext", ex.getAllowedNext(),
                "timestamp", Instant.now().toString()
        ));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.PAYLOAD_TOO_LARGE)
    public Map<String, String> handleMaxUploadSize(MaxUploadSizeExceededException ex) {
        return Map.of("message", "Arquivo muito grande. Limite: 10 MB.");
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
