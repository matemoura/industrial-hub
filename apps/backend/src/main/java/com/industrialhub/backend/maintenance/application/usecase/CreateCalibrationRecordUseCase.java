package com.industrialhub.backend.maintenance.application.usecase;

import com.industrialhub.backend.common.application.AuditService;
import com.industrialhub.backend.common.domain.AuditAction;
import com.industrialhub.backend.common.infrastructure.StorageService;
import com.industrialhub.backend.maintenance.application.dto.CalibrationRecordResponse;
import com.industrialhub.backend.maintenance.application.dto.CreateCalibrationRecordRequest;
import com.industrialhub.backend.maintenance.domain.CalibrationRecord;
import com.industrialhub.backend.maintenance.domain.CalibrationResult;
import com.industrialhub.backend.maintenance.domain.CalibrationSchedule;
import com.industrialhub.backend.maintenance.domain.CalibrationScheduleNotFoundException;
import com.industrialhub.backend.maintenance.domain.Equipment;
import com.industrialhub.backend.maintenance.infrastructure.CalibrationRecordRepository;
import com.industrialhub.backend.maintenance.infrastructure.CalibrationScheduleRepository;
import com.industrialhub.backend.qms.application.dto.CreateNcRequest;
import com.industrialhub.backend.qms.application.dto.NcResponse;
import com.industrialhub.backend.qms.application.usecase.CreateNcUseCase;
import com.industrialhub.backend.qms.domain.NcSeverity;
import com.industrialhub.backend.qms.domain.NcType;
import com.industrialhub.backend.qms.ged.application.usecase.GedFileValidator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

@Service
public class CreateCalibrationRecordUseCase {

    private final CalibrationScheduleRepository scheduleRepository;
    private final CalibrationRecordRepository recordRepository;
    private final StorageService storageService;
    private final GedFileValidator gedFileValidator;
    private final CreateNcUseCase createNcUseCase;
    private final AuditService auditService;

    public CreateCalibrationRecordUseCase(CalibrationScheduleRepository scheduleRepository,
                                           CalibrationRecordRepository recordRepository,
                                           StorageService storageService,
                                           GedFileValidator gedFileValidator,
                                           CreateNcUseCase createNcUseCase,
                                           AuditService auditService) {
        this.scheduleRepository = scheduleRepository;
        this.recordRepository = recordRepository;
        this.storageService = storageService;
        this.gedFileValidator = gedFileValidator;
        this.createNcUseCase = createNcUseCase;
        this.auditService = auditService;
    }

    @Transactional
    public CalibrationRecordResponse execute(CreateCalibrationRecordRequest req,
                                              MultipartFile certificateFile,
                                              String principal) throws IOException {
        // Validação: certificateDocumentId e file são mutuamente exclusivos (ADR-052 Decisão 2)
        if (req.certificateDocumentId() != null && certificateFile != null && !certificateFile.isEmpty()) {
            throw new IllegalArgumentException("certificate-conflict");
        }

        // TOCTOU fix: lock pessimista (ADR-049 §6)
        CalibrationSchedule schedule = scheduleRepository.findByIdForUpdate(req.scheduleId())
            .orElseThrow(() -> new CalibrationScheduleNotFoundException(req.scheduleId()));

        Equipment equipment = schedule.getEquipment();

        // Upload de certificado PDF se arquivo foi enviado
        String certificateStoragePath = null;
        if (certificateFile != null && !certificateFile.isEmpty()) {
            gedFileValidator.validate(certificateFile);
            String filename = GedFileValidator.sanitizeFilename(certificateFile.getOriginalFilename());
            String key = "calibration/%s/%s_%s".formatted(schedule.getId(), UUID.randomUUID(), filename);
            storageService.upload(key, certificateFile.getInputStream(),
                certificateFile.getContentType(), certificateFile.getSize());
            certificateStoragePath = key;
        }

        // Atualiza datas do plano
        schedule.setLastCalibratedAt(req.calibratedAt());
        schedule.setNextDueAt(req.calibratedAt().plusDays(schedule.getIntervalDays()));

        CalibrationRecord record = CalibrationRecord.builder()
            .schedule(schedule)
            .equipment(equipment)
            .calibratedAt(req.calibratedAt())
            .result(req.result())
            .technician(req.technician())
            .notes(req.notes())
            .certificateDocumentId(req.certificateDocumentId())
            .certificateStoragePath(certificateStoragePath)
            .recordedBy(principal)
            .build();

        // Auto-NC quando resultado fora de tolerância (ADR-052 Decisão 3)
        if (req.result() == CalibrationResult.OUT_OF_TOLERANCE) {
            CreateNcRequest ncRequest = new CreateNcRequest(
                "Calibração fora de tolerância: " + equipment.getCode(),
                "Equipamento " + equipment.getName()
                    + " apresentou resultado OUT_OF_TOLERANCE na calibração de " + req.calibratedAt(),
                NcType.EQUIPMENT,
                NcSeverity.HIGH,
                null
            );
            NcResponse nc = createNcUseCase.execute(ncRequest, "system");
            record.setAutoNcId(nc.id());
        }

        record = recordRepository.save(record);

        auditService.log(principal, AuditAction.CALIBRATION_RECORD_CREATED,
            "CalibrationRecord", record.getId(),
            Map.of("equipmentCode", equipment.getCode(),
                   "result", req.result().name(),
                   "calibratedAt", req.calibratedAt().toString()));

        return CalibrationRecordResponse.from(record);
    }
}
