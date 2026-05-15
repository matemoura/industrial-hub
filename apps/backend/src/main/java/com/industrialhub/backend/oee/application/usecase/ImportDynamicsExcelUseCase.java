package com.industrialhub.backend.oee.application.usecase;

import com.industrialhub.backend.common.application.AuditService;
import com.industrialhub.backend.common.domain.AuditAction;
import com.industrialhub.backend.oee.application.dto.ImportResultDto;
import com.industrialhub.backend.oee.application.parser.DynamicsExcelParser;
import com.industrialhub.backend.oee.application.parser.ParseResult;
import com.industrialhub.backend.oee.domain.ImportBatch;
import com.industrialhub.backend.oee.domain.TimeRecord;
import com.industrialhub.backend.oee.infrastructure.ImportBatchRepository;
import com.industrialhub.backend.oee.infrastructure.TimeRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
public class ImportDynamicsExcelUseCase {

    private static final Logger log = LoggerFactory.getLogger(ImportDynamicsExcelUseCase.class);

    private final DynamicsExcelParser parser;
    private final ImportBatchRepository batchRepository;
    private final TimeRecordRepository timeRecordRepository;
    private final AuditService auditService;

    public ImportDynamicsExcelUseCase(DynamicsExcelParser parser,
                                      ImportBatchRepository batchRepository,
                                      TimeRecordRepository timeRecordRepository,
                                      AuditService auditService) {
        this.parser = parser;
        this.batchRepository = batchRepository;
        this.timeRecordRepository = timeRecordRepository;
        this.auditService = auditService;
    }

    @Transactional
    public ImportResultDto execute(MultipartFile file, boolean overwrite, String username) {
        String rawFileName = file.getOriginalFilename();
        String fileName = rawFileName != null ? rawFileName.replaceAll("[\r\n\t]", "_") : "unknown";
        log.info("Iniciando importação do arquivo: {}", fileName);

        ParseResult parsed;
        try {
            parsed = parser.parse(file.getInputStream(), fileName);
        } catch (IOException e) {
            throw new InvalidExcelFormatException("Erro ao ler o arquivo: " + e.getMessage());
        }

        boolean replaced = false;
        var existing = batchRepository.findByPeriodDate(parsed.periodDate());
        if (existing.isPresent()) {
            if (!overwrite) {
                throw new DuplicateImportException(existing.get().getId(), existing.get().getPeriodDate());
            }
            timeRecordRepository.deleteAllByBatch(existing.get());
            batchRepository.delete(existing.get());
            batchRepository.flush();
            replaced = true;
            log.info("Overwrite: registros do período {} removidos", parsed.periodDate());
        }

        ImportBatch batch = batchRepository.save(ImportBatch.builder()
                .fileName(fileName)
                .importedAt(Instant.now())
                .periodDate(parsed.periodDate())
                .totalRecords(parsed.rows().size())
                .workerCount(parsed.workerCount())
                .build());

        List<TimeRecord> records = parsed.rows().stream()
                .map(row -> TimeRecord.builder()
                        .batch(batch)
                        .workerId(row.workerId())
                        .workerName(row.workerName())
                        .profileDate(row.profileDate())
                        .startTime(row.startTime())
                        .endTime(row.endTime())
                        .recordType(row.recordType())
                        .reference(row.reference())
                        .operationNumber(row.operationNumber())
                        .workIdentifier(row.workIdentifier())
                        .description(row.description())
                        .hours(row.hours())
                        .build())
                .toList();

        timeRecordRepository.saveAll(records);

        log.info("Importação concluída: {} registros, {} trabalhadores, data {}",
                records.size(), parsed.workerCount(), parsed.periodDate());

        auditService.log(username, AuditAction.IMPORT_CREATED, "ImportBatch", batch.getId(),
                Map.of("periodDate", batch.getPeriodDate().toString(),
                       "rowCount", records.size(),
                       "replaced", replaced));

        return new ImportResultDto(
                batch.getId(),
                batch.getPeriodDate(),
                batch.getWorkerCount(),
                records.size(),
                parsed.skippedCount(),
                parsed.errors(),
                replaced
        );
    }
}
