package com.industrialhub.backend.maintenance;

import com.industrialhub.backend.common.application.AuditService;
import com.industrialhub.backend.common.infrastructure.StorageService;
import com.industrialhub.backend.maintenance.application.dto.CalibrationRecordResponse;
import com.industrialhub.backend.maintenance.application.dto.CreateCalibrationRecordRequest;
import com.industrialhub.backend.maintenance.application.usecase.CreateCalibrationRecordUseCase;
import com.industrialhub.backend.maintenance.domain.CalibrationRecord;
import com.industrialhub.backend.maintenance.domain.CalibrationResult;
import com.industrialhub.backend.maintenance.domain.CalibrationSchedule;
import com.industrialhub.backend.maintenance.domain.CalibrationScheduleNotFoundException;
import com.industrialhub.backend.maintenance.domain.Equipment;
import com.industrialhub.backend.maintenance.domain.EquipmentStatus;
import com.industrialhub.backend.maintenance.domain.EquipmentType;
import com.industrialhub.backend.maintenance.infrastructure.CalibrationRecordRepository;
import com.industrialhub.backend.maintenance.infrastructure.CalibrationScheduleRepository;
import com.industrialhub.backend.qms.application.dto.CreateNcRequest;
import com.industrialhub.backend.qms.application.dto.NcResponse;
import com.industrialhub.backend.qms.application.usecase.CreateNcUseCase;
import com.industrialhub.backend.qms.domain.NcSeverity;
import com.industrialhub.backend.qms.domain.NcStatus;
import com.industrialhub.backend.qms.domain.NcType;
import com.industrialhub.backend.qms.ged.application.usecase.GedFileValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * US-121 ACs cobertos:
 * (b) certificateDocumentId + file ambos â†’ IllegalArgumentException("certificate-conflict") â†’ 400
 * (a) nextDueAt e lastCalibratedAt atualizados apÃ³s registro
 *
 * US-122 ACs cobertos:
 * AC6: result == OUT_OF_TOLERANCE â†’ CreateNcUseCase chamado com principal="system", autoNcId preenchido
 * AC7: result == IN_TOLERANCE â†’ CreateNcUseCase NÃƒO chamado; autoNcId = null
 */
@ExtendWith(MockitoExtension.class)
class CreateCalibrationRecordUseCaseTest {

    @Mock private CalibrationScheduleRepository scheduleRepository;
    @Mock private CalibrationRecordRepository recordRepository;
    @Mock private StorageService storageService;
    @Mock private GedFileValidator gedFileValidator;
    @Mock private CreateNcUseCase createNcUseCase;
    @Mock private AuditService auditService;

    private CreateCalibrationRecordUseCase useCase;

    private Equipment equipment;
    private CalibrationSchedule schedule;

    @BeforeEach
    void setUp() {
        useCase = new CreateCalibrationRecordUseCase(
                scheduleRepository, recordRepository,
                storageService, gedFileValidator, createNcUseCase, auditService);

        equipment = Equipment.builder()
                .id(UUID.randomUUID())
                .code("EQ-002")
                .name("TermÃ´metro digital")
                .type(EquipmentType.TOOL)
                .status(EquipmentStatus.OPERATIONAL)
                .active(true)
                .build();

        schedule = CalibrationSchedule.builder()
                .id(UUID.randomUUID())
                .equipment(equipment)
                .intervalDays(30)
                .nextDueAt(LocalDate.now())
                .active(true)
                .createdBy("supervisor1")
                .build();
    }

    // â”€â”€â”€ AC (b) US-121: cert+file â†’ IllegalArgumentException â†’ 400 â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Test
    void shouldThrow_whenBothCertificateDocumentIdAndFileProvided() throws IOException {
        // AC US-121 (b): certificateDocumentId + file mutuamente exclusivos â†’ 400
        UUID certDocId = UUID.randomUUID();
        var request = new CreateCalibrationRecordRequest(
                schedule.getId(), LocalDate.now(), CalibrationResult.IN_TOLERANCE,
                "TÃ©cnico Silva", null, certDocId);

        // mock MultipartFile com conteÃºdo (nÃ£o vazio)
        MultipartFile certFile = mock(MultipartFile.class);
        when(certFile.isEmpty()).thenReturn(false);

        assertThatThrownBy(() -> useCase.execute(request, certFile, "supervisor1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("certificate-conflict");
    }

    // â”€â”€â”€ AC (a) US-121: nextDueAt e lastCalibratedAt atualizados â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Test
    void shouldUpdateScheduleDates_afterRecord() throws IOException {
        // AC US-121 (a): schedule.lastCalibratedAt = calibratedAt; nextDueAt = calibratedAt + intervalDays
        LocalDate calibratedAt = LocalDate.now().minusDays(1);
        var request = new CreateCalibrationRecordRequest(
                schedule.getId(), calibratedAt, CalibrationResult.IN_TOLERANCE,
                "TÃ©cnico Silva", null, null);

        when(scheduleRepository.findByIdForUpdate(schedule.getId()))
                .thenReturn(Optional.of(schedule));
        when(recordRepository.save(any())).thenAnswer(inv -> {
            CalibrationRecord r = inv.getArgument(0);
            r.setId(UUID.randomUUID());
            r.setRecordedAt(LocalDateTime.now());
            return r;
        });

        CalibrationRecordResponse response = useCase.execute(request, null, "supervisor1");

        assertThat(schedule.getLastCalibratedAt()).isEqualTo(calibratedAt);
        assertThat(schedule.getNextDueAt()).isEqualTo(calibratedAt.plusDays(schedule.getIntervalDays()));
        assertThat(response.result()).isEqualTo(CalibrationResult.IN_TOLERANCE);
    }

    // â”€â”€â”€ AC6 US-122: OUT_OF_TOLERANCE â†’ NC criada com principal="system" â”€â”€â”€â”€â”€

    @Test
    void shouldCallCreateNcUseCase_whenResultIsOutOfTolerance() throws IOException {
        // AC US-122 AC6: fora de tolerÃ¢ncia â†’ NC automÃ¡tica criada
        UUID ncId = UUID.randomUUID();
        NcResponse mockNcResponse = buildNcResponse(ncId);

        var request = new CreateCalibrationRecordRequest(
                schedule.getId(), LocalDate.now(), CalibrationResult.OUT_OF_TOLERANCE,
                "TÃ©cnico Lima", "Desvio crÃ­tico de temperatura", null);

        when(scheduleRepository.findByIdForUpdate(schedule.getId()))
                .thenReturn(Optional.of(schedule));
        when(createNcUseCase.execute(any(CreateNcRequest.class), eq("system")))
                .thenReturn(mockNcResponse);
        when(recordRepository.save(any())).thenAnswer(inv -> {
            CalibrationRecord r = inv.getArgument(0);
            r.setId(UUID.randomUUID());
            r.setRecordedAt(LocalDateTime.now());
            return r;
        });

        CalibrationRecordResponse response = useCase.execute(request, null, "supervisor1");

        // NC chamada com principal "system" (ADR-052 DecisÃ£o 3)
        ArgumentCaptor<String> principalCaptor = ArgumentCaptor.forClass(String.class);
        verify(createNcUseCase).execute(any(CreateNcRequest.class), principalCaptor.capture());
        assertThat(principalCaptor.getValue()).isEqualTo("system");
        assertThat(response.autoNcId()).isEqualTo(ncId);
    }

    @Test
    void shouldPopulateAutoNcId_inRecord_whenOutOfTolerance() throws IOException {
        // AC US-122 AC6: autoNcId preenchido no registro salvo
        UUID ncId = UUID.randomUUID();
        NcResponse mockNcResponse = buildNcResponse(ncId);

        var request = new CreateCalibrationRecordRequest(
                schedule.getId(), LocalDate.now(), CalibrationResult.OUT_OF_TOLERANCE,
                "TÃ©cnico Lima", null, null);

        when(scheduleRepository.findByIdForUpdate(schedule.getId()))
                .thenReturn(Optional.of(schedule));
        when(createNcUseCase.execute(any(), any())).thenReturn(mockNcResponse);

        ArgumentCaptor<CalibrationRecord> recordCaptor = ArgumentCaptor.forClass(CalibrationRecord.class);
        when(recordRepository.save(recordCaptor.capture())).thenAnswer(inv -> {
            CalibrationRecord r = inv.getArgument(0);
            r.setId(UUID.randomUUID());
            r.setRecordedAt(LocalDateTime.now());
            return r;
        });

        useCase.execute(request, null, "supervisor1");

        assertThat(recordCaptor.getValue().getAutoNcId()).isEqualTo(ncId);
    }

    // â”€â”€â”€ AC7 US-122: IN_TOLERANCE â†’ NC NÃƒO chamada â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Test
    void shouldNotCallCreateNcUseCase_whenResultIsInTolerance() throws IOException {
        // AC US-122 AC7: dentro da tolerÃ¢ncia â†’ sem NC automÃ¡tica
        var request = new CreateCalibrationRecordRequest(
                schedule.getId(), LocalDate.now(), CalibrationResult.IN_TOLERANCE,
                "TÃ©cnico Costa", null, null);

        when(scheduleRepository.findByIdForUpdate(schedule.getId()))
                .thenReturn(Optional.of(schedule));
        when(recordRepository.save(any())).thenAnswer(inv -> {
            CalibrationRecord r = inv.getArgument(0);
            r.setId(UUID.randomUUID());
            r.setRecordedAt(LocalDateTime.now());
            return r;
        });

        CalibrationRecordResponse response = useCase.execute(request, null, "supervisor1");

        verify(createNcUseCase, never()).execute(any(), any());
        assertThat(response.autoNcId()).isNull();
    }

    @Test
    void shouldNotCallCreateNcUseCase_whenResultIsAdjusted() throws IOException {
        // AC US-122 AC7 (extensÃ£o): ADJUSTED tambÃ©m nÃ£o gera NC automÃ¡tica
        var request = new CreateCalibrationRecordRequest(
                schedule.getId(), LocalDate.now(), CalibrationResult.ADJUSTED,
                "TÃ©cnico Costa", null, null);

        when(scheduleRepository.findByIdForUpdate(schedule.getId()))
                .thenReturn(Optional.of(schedule));
        when(recordRepository.save(any())).thenAnswer(inv -> {
            CalibrationRecord r = inv.getArgument(0);
            r.setId(UUID.randomUUID());
            r.setRecordedAt(LocalDateTime.now());
            return r;
        });

        CalibrationRecordResponse response = useCase.execute(request, null, "supervisor1");

        verify(createNcUseCase, never()).execute(any(), any());
        assertThat(response.autoNcId()).isNull();
    }

    @Test
    void shouldThrow_whenScheduleNotFound() {
        UUID unknownId = UUID.randomUUID();
        var request = new CreateCalibrationRecordRequest(
                unknownId, LocalDate.now(), CalibrationResult.IN_TOLERANCE,
                "TÃ©cnico", null, null);

        when(scheduleRepository.findByIdForUpdate(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(request, null, "supervisor1"))
                .isInstanceOf(CalibrationScheduleNotFoundException.class);
    }

    // â”€â”€â”€ helpers â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    // --- AC8 US-121: hasCertificate calculado corretamente pelo mapper ---------

    @Test
    void shouldSetHasCertificate_true_whenCertificateDocumentIdProvided() throws IOException {
        UUID certDocId = UUID.randomUUID();
        var request = new CreateCalibrationRecordRequest(
                schedule.getId(), LocalDate.now(), CalibrationResult.IN_TOLERANCE,
                "Tecnico Silva", null, certDocId);

        when(scheduleRepository.findByIdForUpdate(schedule.getId()))
                .thenReturn(Optional.of(schedule));
        when(recordRepository.save(any())).thenAnswer(inv -> {
            CalibrationRecord r = inv.getArgument(0);
            r.setId(UUID.randomUUID());
            r.setRecordedAt(LocalDateTime.now());
            return r;
        });

        CalibrationRecordResponse response = useCase.execute(request, null, "supervisor1");

        assertThat(response.hasCertificate()).isTrue();
        assertThat(response.certificateDocumentId()).isEqualTo(certDocId);
    }

    @Test
    void shouldSetHasCertificate_false_whenNoCertificateProvided() throws IOException {
        var request = new CreateCalibrationRecordRequest(
                schedule.getId(), LocalDate.now(), CalibrationResult.IN_TOLERANCE,
                "Tecnico Costa", null, null);

        when(scheduleRepository.findByIdForUpdate(schedule.getId()))
                .thenReturn(Optional.of(schedule));
        when(recordRepository.save(any())).thenAnswer(inv -> {
            CalibrationRecord r = inv.getArgument(0);
            r.setId(UUID.randomUUID());
            r.setRecordedAt(LocalDateTime.now());
            return r;
        });

        CalibrationRecordResponse response = useCase.execute(request, null, "supervisor1");

        assertThat(response.hasCertificate()).isFalse();
        assertThat(response.certificateDocumentId()).isNull();
    }

    private NcResponse buildNcResponse(UUID ncId) {
        return new NcResponse(
                ncId,
                "CalibraÃ§Ã£o fora de tolerÃ¢ncia: " + equipment.getCode(),
                "Equipamento fora de tolerÃ¢ncia",
                NcType.EQUIPMENT,
                NcSeverity.HIGH,
                NcStatus.OPEN,
                "system",
                LocalDateTime.now(),
                null,  // closedAt
                null,  // closedBy
                null,  // supplierId
                null,  // supplierName
                List.of(), // actions
                null   // rca
        );
    }
}
