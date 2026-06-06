package com.industrialhub.backend.maintenance.application.dto;

import com.industrialhub.backend.maintenance.domain.CalibrationRecord;
import com.industrialhub.backend.maintenance.domain.CalibrationResult;

import java.time.LocalDate;
import java.util.UUID;

public record CalibrationRecordResponse(
    UUID id,
    UUID scheduleId,
    String equipmentCode,
    LocalDate calibratedAt,
    CalibrationResult result,
    String technician,
    String notes,
    boolean hasCertificate,
    UUID certificateDocumentId,
    UUID autoNcId,
    // SEC-159: recordedBy retido na entidade para auditoria, mas não exposto via API (ADR-049 §4)
    LocalDate recordedAt
) {
    public static CalibrationRecordResponse from(CalibrationRecord r) {
        boolean hasCertificate = r.getCertificateDocumentId() != null
                || r.getCertificateStoragePath() != null;
        return new CalibrationRecordResponse(
            r.getId(),
            r.getSchedule().getId(),
            r.getEquipment().getCode(),
            r.getCalibratedAt(),
            r.getResult(),
            r.getTechnician(),
            r.getNotes(),
            hasCertificate,
            r.getCertificateDocumentId(),
            r.getAutoNcId(),
            r.getRecordedAt().toLocalDate()
        );
    }
}
