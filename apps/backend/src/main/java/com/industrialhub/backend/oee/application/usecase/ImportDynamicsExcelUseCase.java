package com.industrialhub.backend.oee.application.usecase;

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

@Service
public class ImportDynamicsExcelUseCase {

    private static final Logger log = LoggerFactory.getLogger(ImportDynamicsExcelUseCase.class);

    private final DynamicsExcelParser parser;
    private final ImportBatchRepository batchRepository;
    private final TimeRecordRepository timeRecordRepository;

    public ImportDynamicsExcelUseCase(DynamicsExcelParser parser,
                                      ImportBatchRepository batchRepository,
                                      TimeRecordRepository timeRecordRepository) {
        this.parser = parser;
        this.batchRepository = batchRepository;
        this.timeRecordRepository = timeRecordRepository;
    }

    @Transactional
    public ImportResultDto execute(MultipartFile file) {
        String rawFileName = file.getOriginalFilename();
        String fileName = rawFileName != null ? rawFileName.replaceAll("[\r\n\t]", "_") : "unknown";
        log.info("Iniciando importação do arquivo: {}", fileName);

        ParseResult parsed;
        try {
            parsed = parser.parse(file.getInputStream(), fileName);
        } catch (IOException e) {
            throw new InvalidExcelFormatException("Erro ao ler o arquivo: " + e.getMessage());
        }

        // 409 se período já importado
        batchRepository.findByPeriodDate(parsed.periodDate()).ifPresent(existing -> {
            throw new DuplicateImportException(existing.getId(), existing.getPeriodDate());
        });

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

        return new ImportResultDto(
                batch.getId(),
                batch.getPeriodDate(),
                batch.getWorkerCount(),
                records.size(),
                parsed.skippedCount(),
                parsed.errors()
        );
    }
}
