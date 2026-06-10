package com.industrialhub.backend.common;

import com.industrialhub.backend.common.domain.AlertThresholdNotFoundException;
import com.industrialhub.backend.common.domain.CannotAnonymizeActiveAdminException;
import com.industrialhub.backend.common.domain.DataRetentionCooldownException;
import com.industrialhub.backend.common.domain.SelfAnonymizationException;
import com.industrialhub.backend.common.domain.UserAlreadyAnonymizedException;
import com.industrialhub.backend.common.domain.PlantNotFoundException;
import com.industrialhub.backend.common.domain.PlantDuplicateCodeException;
import com.industrialhub.backend.common.domain.EscalationCooldownException;
import com.industrialhub.backend.common.domain.InvalidClassifierValueException;
import com.industrialhub.backend.common.domain.AttachmentNotFoundException;
import com.industrialhub.backend.common.domain.SlaRuleDuplicateException;
import com.industrialhub.backend.common.domain.SlaRuleNotFoundException;
import com.industrialhub.backend.common.domain.FileTooLargeException;
import com.industrialhub.backend.common.domain.InvalidFileTypeException;
import com.industrialhub.backend.common.domain.NotificationNotFoundException;
import com.industrialhub.backend.common.domain.ShiftNotFoundException;
import com.industrialhub.backend.common.infrastructure.StorageException;
import com.industrialhub.backend.common.auth.application.usecase.InvalidCredentialsException;
import com.industrialhub.backend.common.auth.domain.InvalidPasswordException;
import com.industrialhub.backend.common.auth.domain.LastAdminException;
import com.industrialhub.backend.common.auth.domain.UserAlreadyExistsException;
import com.industrialhub.backend.common.auth.domain.UserNotFoundException;
import com.industrialhub.backend.common.security.TooManyRequestsException;
import com.industrialhub.backend.maintenance.domain.InactiveEquipmentScheduleException;
import com.industrialhub.backend.maintenance.domain.InactiveSparePartException;
import com.industrialhub.backend.maintenance.domain.InsufficientStockException;
import com.industrialhub.backend.maintenance.domain.InvalidScheduleRecurrenceException;
import com.industrialhub.backend.maintenance.domain.ScheduleNotFoundException;
import com.industrialhub.backend.maintenance.domain.EquipmentDuplicateCodeException;
import com.industrialhub.backend.maintenance.domain.EquipmentHasOpenOrdersException;
import com.industrialhub.backend.maintenance.domain.EquipmentNotFoundException;
import com.industrialhub.backend.maintenance.domain.InvalidWorkOrderTransitionException;
import com.industrialhub.backend.maintenance.domain.SparePartDuplicateCodeException;
import com.industrialhub.backend.maintenance.domain.SparePartNotFoundException;
import com.industrialhub.backend.maintenance.domain.WorkOrderNotFoundException;
import com.industrialhub.backend.maintenance.domain.WorkOrderPartNotFoundException;
import com.industrialhub.backend.oee.application.usecase.DuplicateImportException;
import com.industrialhub.backend.oee.domain.PlannedDowntimeNotFoundException;
import com.industrialhub.backend.oee.application.usecase.InvalidExcelFormatException;
import com.industrialhub.backend.oee.application.validation.InvalidDateRangeException;
import com.industrialhub.backend.qms.domain.ActionNotAllowedException;
import com.industrialhub.backend.qms.domain.ActionNotFoundException;
import com.industrialhub.backend.qms.domain.InvalidNcTransitionException;
import com.industrialhub.backend.qms.domain.NcNotFoundException;
import com.industrialhub.backend.qms.domain.RcaAlreadyExistsException;
import com.industrialhub.backend.qms.domain.RcaNotFoundException;
import com.industrialhub.backend.qms.domain.RcaNotAllowedException;
import com.industrialhub.backend.qms.domain.SupplierDuplicateCodeException;
import com.industrialhub.backend.qms.domain.SupplierNotFoundException;
import com.industrialhub.backend.qms.domain.SupplierRequiredForNcException;
import com.industrialhub.backend.common.domain.EvaluateNowCooldownException;
import com.industrialhub.backend.common.webhook.domain.WebhookInvalidUrlException;
import com.industrialhub.backend.common.webhook.domain.WebhookNotFoundException;
import com.industrialhub.backend.maintenance.domain.CalibrationScheduleNotFoundException;
import com.industrialhub.backend.maintenance.domain.EquipmentDecommissionedException;
import com.industrialhub.backend.training.domain.TrainingCourseCodeAlreadyExistsException;
import com.industrialhub.backend.training.domain.TrainingCourseNotFoundException;
import com.industrialhub.backend.training.domain.TrainingRecordNotFoundException;
import com.industrialhub.backend.production.domain.ImportProductionBatchNotFoundException;
import com.industrialhub.backend.production.domain.ProductNotFoundException;
import com.industrialhub.backend.qms.domain.NcDocumentLinkAlreadyExistsException;
import com.industrialhub.backend.qms.ged.domain.DocumentCodeAlreadyExistsException;
import com.industrialhub.backend.qms.ged.domain.DocumentNotFoundException;
import com.industrialhub.backend.qms.ged.domain.InvalidGedFileException;
import com.industrialhub.backend.qms.ged.domain.InvalidGedTransitionException;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import jakarta.validation.ConstraintViolationException;
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

    @ExceptionHandler(RcaNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleRcaNotFound(RcaNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "message", ex.getMessage(),
                "timestamp", Instant.now().toString()
        ));
    }

    @ExceptionHandler(RcaAlreadyExistsException.class)
    public ResponseEntity<Map<String, Object>> handleRcaAlreadyExists(RcaAlreadyExistsException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "message", ex.getMessage(),
                "timestamp", Instant.now().toString()
        ));
    }

    @ExceptionHandler(RcaNotAllowedException.class)
    public ResponseEntity<Map<String, Object>> handleRcaNotAllowed(RcaNotAllowedException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of(
                "message", ex.getMessage(),
                "timestamp", Instant.now().toString()
        ));
    }

    @ExceptionHandler(InvalidScheduleRecurrenceException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidScheduleRecurrence(InvalidScheduleRecurrenceException ex) {
        return ResponseEntity.badRequest().body(Map.of(
                "message", ex.getMessage(),
                "timestamp", Instant.now().toString()
        ));
    }

    @ExceptionHandler(ScheduleNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleScheduleNotFound(ScheduleNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "message", ex.getMessage(),
                "timestamp", Instant.now().toString()
        ));
    }

    @ExceptionHandler(InactiveEquipmentScheduleException.class)
    public ResponseEntity<Map<String, Object>> handleInactiveEquipmentSchedule(InactiveEquipmentScheduleException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of(
                "message", ex.getMessage(),
                "timestamp", Instant.now().toString()
        ));
    }

    @ExceptionHandler(PlannedDowntimeNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handlePlannedDowntimeNotFound(PlannedDowntimeNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
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

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleUserNotFound(UserNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "message", ex.getMessage(),
                "timestamp", Instant.now().toString()
        ));
    }

    @ExceptionHandler(UserAlreadyExistsException.class)
    public ResponseEntity<Map<String, Object>> handleUserAlreadyExists(UserAlreadyExistsException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "message", ex.getMessage(),
                "timestamp", Instant.now().toString()
        ));
    }

    @ExceptionHandler(LastAdminException.class)
    public ResponseEntity<Map<String, Object>> handleLastAdmin(LastAdminException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of(
                "message", ex.getMessage(),
                "timestamp", Instant.now().toString()
        ));
    }

    @ExceptionHandler(InvalidPasswordException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidPassword(InvalidPasswordException ex) {
        return ResponseEntity.badRequest().body(Map.of(
                "message", ex.getMessage(),
                "timestamp", Instant.now().toString()
        ));
    }

    @ExceptionHandler(SupplierNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleSupplierNotFound(SupplierNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "message", ex.getMessage(),
                "timestamp", Instant.now().toString()
        ));
    }

    @ExceptionHandler(SupplierDuplicateCodeException.class)
    public ResponseEntity<Map<String, Object>> handleSupplierDuplicateCode(SupplierDuplicateCodeException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "message", ex.getMessage(),
                "timestamp", Instant.now().toString()
        ));
    }

    @ExceptionHandler(SupplierRequiredForNcException.class)
    public ResponseEntity<Map<String, Object>> handleSupplierRequired(SupplierRequiredForNcException ex) {
        return ResponseEntity.badRequest().body(Map.of(
                "message", ex.getMessage(),
                "timestamp", Instant.now().toString()
        ));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, Object>> handleConstraintViolation(ConstraintViolationException ex) {
        String message = ex.getConstraintViolations().stream()
                .map(cv -> cv.getPropertyPath() + ": " + cv.getMessage())
                .collect(Collectors.joining("; "));
        return ResponseEntity.badRequest().body(Map.of(
                "message", message.isBlank() ? "Parâmetro inválido" : message,
                "timestamp", Instant.now().toString()
        ));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        String message = "certificate-conflict".equals(ex.getMessage())
            ? "Informe certificateDocumentId ou faça upload, não ambos."
            : ex.getMessage();
        return ResponseEntity.badRequest().body(Map.of(
                "message", message,
                "timestamp", Instant.now().toString()
        ));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalState(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "message", ex.getMessage(),
                "timestamp", Instant.now().toString()
        ));
    }

    @ExceptionHandler(ShiftNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleShiftNotFound(ShiftNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "message", ex.getMessage(),
                "timestamp", Instant.now().toString()
        ));
    }

    @ExceptionHandler(EvaluateNowCooldownException.class)
    public ResponseEntity<Map<String, Object>> handleEvaluateNowCooldown(EvaluateNowCooldownException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(Map.of(
                "message", ex.getMessage(),
                "secondsRemaining", ex.getSecondsRemaining(),
                "timestamp", Instant.now().toString()
        ));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleMethodArgumentTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return ResponseEntity.badRequest().body(Map.of(
                "message", "Invalid value for parameter: " + ex.getName(),
                "timestamp", Instant.now().toString()
        ));
    }

    @ExceptionHandler(AlertThresholdNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleAlertThresholdNotFound(AlertThresholdNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "message", ex.getMessage(),
                "timestamp", Instant.now().toString()
        ));
    }

    @ExceptionHandler(NotificationNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotificationNotFound(NotificationNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "message", ex.getMessage(),
                "timestamp", Instant.now().toString()
        ));
    }

    @ExceptionHandler(SparePartNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleSparePartNotFound(SparePartNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "message", ex.getMessage(),
                "timestamp", Instant.now().toString()
        ));
    }

    @ExceptionHandler(SparePartDuplicateCodeException.class)
    public ResponseEntity<Map<String, Object>> handleSparePartDuplicateCode(SparePartDuplicateCodeException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "message", ex.getMessage(),
                "timestamp", Instant.now().toString()
        ));
    }

    @ExceptionHandler(InactiveSparePartException.class)
    public ResponseEntity<Map<String, Object>> handleInactiveSparePart(InactiveSparePartException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of(
                "message", ex.getMessage(),
                "timestamp", Instant.now().toString()
        ));
    }

    @ExceptionHandler(InsufficientStockException.class)
    public ResponseEntity<Map<String, Object>> handleInsufficientStock(InsufficientStockException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of(
                "message", ex.getMessage(),
                "timestamp", Instant.now().toString()
        ));
    }

    @ExceptionHandler(WorkOrderPartNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleWorkOrderPartNotFound(WorkOrderPartNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "message", ex.getMessage(),
                "timestamp", Instant.now().toString()
        ));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.PAYLOAD_TOO_LARGE)
    public Map<String, String> handleMaxUploadSize(MaxUploadSizeExceededException ex) {
        return Map.of("message", "File exceeds maximum allowed size of 10 MB");
    }

    @ExceptionHandler(SlaRuleNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleSlaRuleNotFound(SlaRuleNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "message", ex.getMessage(),
                "timestamp", Instant.now().toString()
        ));
    }

    @ExceptionHandler(SlaRuleDuplicateException.class)
    public ResponseEntity<Map<String, Object>> handleSlaRuleDuplicate(SlaRuleDuplicateException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "message", ex.getMessage(),
                "timestamp", Instant.now().toString()
        ));
    }

    @ExceptionHandler(FileTooLargeException.class)
    @ResponseStatus(HttpStatus.PAYLOAD_TOO_LARGE)
    public Map<String, String> handleFileTooLarge(FileTooLargeException ex) {
        return Map.of("message", ex.getMessage());
    }

    @ExceptionHandler(AttachmentNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, String> handleAttachmentNotFound(AttachmentNotFoundException ex) {
        return Map.of("message", ex.getMessage());
    }

    @ExceptionHandler(InvalidFileTypeException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleInvalidFileType(InvalidFileTypeException ex) {
        return Map.of("message", ex.getMessage());
    }

    @ExceptionHandler(StorageException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    public Map<String, String> handleStorage(StorageException ex) {
        return Map.of("message", ex.getMessage());
    }

    @ExceptionHandler(PlantNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handlePlantNotFound(PlantNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "message", ex.getMessage(),
                "timestamp", Instant.now().toString()
        ));
    }

    @ExceptionHandler(PlantDuplicateCodeException.class)
    public ResponseEntity<Map<String, Object>> handlePlantDuplicateCode(PlantDuplicateCodeException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "message", ex.getMessage(),
                "timestamp", Instant.now().toString()
        ));
    }

    @ExceptionHandler(EscalationCooldownException.class)
    public ResponseEntity<Map<String, Object>> handleEscalationCooldown(EscalationCooldownException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(Map.of(
                "message", ex.getMessage(),
                "secondsRemaining", ex.getSecondsRemaining(),
                "timestamp", Instant.now().toString()
        ));
    }

    @ExceptionHandler(InvalidClassifierValueException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidClassifierValue(InvalidClassifierValueException ex) {
        return ResponseEntity.badRequest().body(Map.of(
                "message", ex.getMessage(),
                "timestamp", Instant.now().toString()
        ));
    }

    @ExceptionHandler(UserAlreadyAnonymizedException.class)
    public ResponseEntity<Map<String, Object>> handleUserAlreadyAnonymized(UserAlreadyAnonymizedException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of(
                "message", ex.getMessage(),
                "timestamp", Instant.now().toString()
        ));
    }

    @ExceptionHandler(SelfAnonymizationException.class)
    public ResponseEntity<Map<String, Object>> handleSelfAnonymization(SelfAnonymizationException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of(
                "message", ex.getMessage(),
                "timestamp", Instant.now().toString()
        ));
    }

    @ExceptionHandler(CannotAnonymizeActiveAdminException.class)
    public ResponseEntity<Map<String, Object>> handleCannotAnonymizeActiveAdmin(CannotAnonymizeActiveAdminException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of(
                "message", ex.getMessage(),
                "timestamp", Instant.now().toString()
        ));
    }

    @ExceptionHandler(WebhookNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleWebhookNotFound(WebhookNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "message", ex.getMessage(),
                "timestamp", Instant.now().toString()
        ));
    }

    @ExceptionHandler(WebhookInvalidUrlException.class)
    public ResponseEntity<Map<String, Object>> handleWebhookInvalidUrl(WebhookInvalidUrlException ex) {
        return ResponseEntity.badRequest().body(Map.of(
                "message", ex.getMessage(),
                "timestamp", Instant.now().toString()
        ));
    }

    @ExceptionHandler(DataRetentionCooldownException.class)
    public ResponseEntity<Map<String, Object>> handleDataRetentionCooldown(DataRetentionCooldownException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(Map.of(
                "message", ex.getMessage(),
                "secondsRemaining", ex.getSecondsRemaining(),
                "timestamp", Instant.now().toString()
        ));
    }

    @ExceptionHandler(ProductNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleProductNotFound(ProductNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "message", ex.getMessage(),
                "timestamp", Instant.now().toString()
        ));
    }

    @ExceptionHandler(DocumentNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleDocumentNotFound(DocumentNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "message", ex.getMessage(),
                "timestamp", Instant.now().toString()
        ));
    }

    @ExceptionHandler(InvalidGedTransitionException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidGedTransition(InvalidGedTransitionException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of(
                "message", ex.getMessage(),
                "timestamp", Instant.now().toString()
        ));
    }

    // SEC-125: GED file MIME type / size validation failure → 422
    @ExceptionHandler(InvalidGedFileException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidGedFile(InvalidGedFileException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of(
                "message", ex.getMessage(),
                "timestamp", Instant.now().toString()
        ));
    }

    // SEC-129: GED document with duplicate code → 409
    @ExceptionHandler(DocumentCodeAlreadyExistsException.class)
    public ResponseEntity<Map<String, Object>> handleDocumentCodeAlreadyExists(DocumentCodeAlreadyExistsException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "message", ex.getMessage(),
                "timestamp", Instant.now().toString()
        ));
    }

    // Sprint 39 / ADR-050 Decisão 2: NC↔GED link duplicate → 409
    @ExceptionHandler(NcDocumentLinkAlreadyExistsException.class)
    public ResponseEntity<Map<String, Object>> handleNcDocumentLinkAlreadyExists(NcDocumentLinkAlreadyExistsException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "message", ex.getMessage(),
                "timestamp", Instant.now().toString()
        ));
    }

    // Sprint 39: link NC↔GED não encontrado para DELETE semântico → 404
    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleEntityNotFound(EntityNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "message", ex.getMessage(),
                "timestamp", Instant.now().toString()
        ));
    }

    // Sprint 41 — Calibração (ISO 13485 §7.6)
    @ExceptionHandler(CalibrationScheduleNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleCalibrationScheduleNotFound(
            CalibrationScheduleNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "message", ex.getMessage(),
                "timestamp", Instant.now().toString()
        ));
    }

    @ExceptionHandler(EquipmentDecommissionedException.class)
    public ResponseEntity<Map<String, Object>> handleEquipmentDecommissioned(
            EquipmentDecommissionedException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of(
                "message", ex.getMessage(),
                "timestamp", Instant.now().toString()
        ));
    }

    // Sprint 40 — Training module (ISO 13485 §6.2)
    @ExceptionHandler(TrainingCourseNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleTrainingCourseNotFound(TrainingCourseNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "message", ex.getMessage(),
                "timestamp", Instant.now().toString()
        ));
    }

    @ExceptionHandler(TrainingCourseCodeAlreadyExistsException.class)
    public ResponseEntity<Map<String, Object>> handleTrainingCourseCodeAlreadyExists(TrainingCourseCodeAlreadyExistsException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "message", ex.getMessage(),
                "timestamp", Instant.now().toString()
        ));
    }

    @ExceptionHandler(TrainingRecordNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleTrainingRecordNotFound(TrainingRecordNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "message", ex.getMessage(),
                "timestamp", Instant.now().toString()
        ));
    }

    @ExceptionHandler(ImportProductionBatchNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleImportProductionBatchNotFound(ImportProductionBatchNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "message", ex.getMessage(),
                "timestamp", Instant.now().toString()
        ));
    }

    @ExceptionHandler(com.industrialhub.backend.production.domain.SterilizationLoadNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleSterilizationLoadNotFound(
            com.industrialhub.backend.production.domain.SterilizationLoadNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "message", ex.getMessage(),
                "timestamp", Instant.now().toString()
        ));
    }

    @ExceptionHandler(com.industrialhub.backend.production.domain.ProductionOrderNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleProductionOrderNotFound(
            com.industrialhub.backend.production.domain.ProductionOrderNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "message", ex.getMessage(),
                "timestamp", Instant.now().toString()
        ));
    }

    @ExceptionHandler(com.industrialhub.backend.production.domain.InvalidLoadTransitionException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidLoadTransition(
            com.industrialhub.backend.production.domain.InvalidLoadTransitionException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of(
                "message", ex.getMessage(),
                "timestamp", Instant.now().toString()
        ));
    }

    @ExceptionHandler(com.industrialhub.backend.production.domain.OrderAlreadyAllocatedException.class)
    public ResponseEntity<Map<String, Object>> handleOrderAlreadyAllocated(
            com.industrialhub.backend.production.domain.OrderAlreadyAllocatedException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "message", ex.getMessage(),
                "timestamp", Instant.now().toString()
        ));
    }

    @ExceptionHandler(com.industrialhub.backend.production.domain.MrpSuggestionNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleMrpSuggestionNotFound(
            com.industrialhub.backend.production.domain.MrpSuggestionNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "message", ex.getMessage(),
                "timestamp", Instant.now().toString()
        ));
    }

    @ExceptionHandler(com.industrialhub.backend.production.domain.InvalidMrpSuggestionStatusException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidMrpSuggestionStatus(
            com.industrialhub.backend.production.domain.InvalidMrpSuggestionStatusException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "message", ex.getMessage(),
                "timestamp", Instant.now().toString()
        ));
    }

    @ExceptionHandler(com.industrialhub.backend.production.domain.NoMrpRunException.class)
    public ResponseEntity<Map<String, Object>> handleNoMrpRun(
            com.industrialhub.backend.production.domain.NoMrpRunException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "message", ex.getMessage(),
                "timestamp", Instant.now().toString()
        ));
    }

    // Sprint 42 — Auditorias Internas (ISO 13485 §8.2.4)
    @ExceptionHandler(com.industrialhub.backend.qms.audit.domain.InternalAuditNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleInternalAuditNotFound(
            com.industrialhub.backend.qms.audit.domain.InternalAuditNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "message", ex.getMessage(),
                "timestamp", Instant.now().toString()
        ));
    }

    @ExceptionHandler(com.industrialhub.backend.qms.audit.domain.AuditChecklistItemNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleAuditChecklistItemNotFound(
            com.industrialhub.backend.qms.audit.domain.AuditChecklistItemNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "message", ex.getMessage(),
                "timestamp", Instant.now().toString()
        ));
    }

    @ExceptionHandler(com.industrialhub.backend.qms.audit.domain.AuditFindingNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleAuditFindingNotFound(
            com.industrialhub.backend.qms.audit.domain.AuditFindingNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "message", ex.getMessage(),
                "timestamp", Instant.now().toString()
        ));
    }

    @ExceptionHandler(com.industrialhub.backend.qms.audit.domain.InvalidAuditStatusTransitionException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidAuditStatusTransition(
            com.industrialhub.backend.qms.audit.domain.InvalidAuditStatusTransitionException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of(
                "message", ex.getMessage(),
                "timestamp", Instant.now().toString()
        ));
    }

    @ExceptionHandler(com.industrialhub.backend.qms.audit.domain.AuditCodeAlreadyExistsException.class)
    public ResponseEntity<Map<String, Object>> handleAuditCodeAlreadyExists(
            com.industrialhub.backend.qms.audit.domain.AuditCodeAlreadyExistsException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "message", ex.getMessage(),
                "timestamp", Instant.now().toString()
        ));
    }

    // Sprint 43 — Gestão de Risco / FMEA (ISO 14971)
    @ExceptionHandler(com.industrialhub.backend.qms.risk.domain.RiskItemNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleRiskItemNotFound(
            com.industrialhub.backend.qms.risk.domain.RiskItemNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "message", ex.getMessage(),
                "timestamp", Instant.now().toString()
        ));
    }

    @ExceptionHandler(com.industrialhub.backend.qms.risk.domain.RiskMitigationActionNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleRiskMitigationActionNotFound(
            com.industrialhub.backend.qms.risk.domain.RiskMitigationActionNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "message", ex.getMessage(),
                "timestamp", Instant.now().toString()
        ));
    }

    @ExceptionHandler(com.industrialhub.backend.qms.risk.domain.InvalidRiskStatusTransitionException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidRiskStatusTransition(
            com.industrialhub.backend.qms.risk.domain.InvalidRiskStatusTransitionException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of(
                "message", ex.getMessage(),
                "timestamp", Instant.now().toString()
        ));
    }

    // Sprint 44 — Controle de Mudanças (ISO 13485 §4.1)
    @ExceptionHandler(com.industrialhub.backend.common.changes.domain.ChangeRequestNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleChangeRequestNotFound(
            com.industrialhub.backend.common.changes.domain.ChangeRequestNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "message", ex.getMessage(),
                "timestamp", Instant.now().toString()
        ));
    }

    @ExceptionHandler(com.industrialhub.backend.common.changes.domain.ChangeRequestLinkNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleChangeRequestLinkNotFound(
            com.industrialhub.backend.common.changes.domain.ChangeRequestLinkNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "message", ex.getMessage(),
                "timestamp", Instant.now().toString()
        ));
    }

    @ExceptionHandler(com.industrialhub.backend.common.changes.domain.InvalidChangeStatusTransitionException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidChangeStatusTransition(
            com.industrialhub.backend.common.changes.domain.InvalidChangeStatusTransitionException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of(
                "message", ex.getMessage(),
                "timestamp", Instant.now().toString()
        ));
    }

    @ExceptionHandler(com.industrialhub.backend.common.changes.domain.ChangeRequestForbiddenException.class)
    public ResponseEntity<Map<String, Object>> handleChangeRequestForbidden(
            com.industrialhub.backend.common.changes.domain.ChangeRequestForbiddenException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                "message", ex.getMessage(),
                "timestamp", Instant.now().toString()
        ));
    }

    @ExceptionHandler(com.industrialhub.backend.common.changes.domain.ChangeRequestCodeConflictException.class)
    public ResponseEntity<Map<String, Object>> handleChangeRequestCodeConflict(
            com.industrialhub.backend.common.changes.domain.ChangeRequestCodeConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "message", ex.getMessage(),
                "timestamp", Instant.now().toString()
        ));
    }

    // Sprint 46 — Análise Crítica pela Direção (ISO 13485 §5.6)
    @ExceptionHandler(com.industrialhub.backend.common.domain.InvalidManagementReviewPeriodException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidManagementReviewPeriod(
            com.industrialhub.backend.common.domain.InvalidManagementReviewPeriodException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "message", ex.getMessage(),
                "timestamp", Instant.now().toString()
        ));
    }

    // Sprint 45 — Reclamações de Clientes + MDR (ISO 13485 §8.2.1)
    @ExceptionHandler(com.industrialhub.backend.qms.complaints.domain.CustomerComplaintNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleComplaintNotFound(
            com.industrialhub.backend.qms.complaints.domain.CustomerComplaintNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "message", ex.getMessage(),
                "timestamp", Instant.now().toString()
        ));
    }

    @ExceptionHandler(com.industrialhub.backend.qms.complaints.domain.InvalidComplaintStatusTransitionException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidComplaintStatusTransition(
            com.industrialhub.backend.qms.complaints.domain.InvalidComplaintStatusTransitionException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of(
                "message", ex.getMessage(),
                "timestamp", Instant.now().toString()
        ));
    }

    @ExceptionHandler(com.industrialhub.backend.qms.complaints.domain.ComplaintCodeConflictException.class)
    public ResponseEntity<Map<String, Object>> handleComplaintCodeConflict(
            com.industrialhub.backend.qms.complaints.domain.ComplaintCodeConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "message", ex.getMessage(),
                "timestamp", Instant.now().toString()
        ));
    }

    @ExceptionHandler(com.industrialhub.backend.qms.complaints.domain.ComplaintClosedException.class)
    public ResponseEntity<Map<String, Object>> handleComplaintClosed(
            com.industrialhub.backend.qms.complaints.domain.ComplaintClosedException ex) {
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
