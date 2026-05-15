package com.industrialhub.backend.oee.application.usecase;

import com.industrialhub.backend.oee.application.dto.ImportResultDto;
import com.industrialhub.backend.oee.application.parser.DynamicsExcelParser;
import com.industrialhub.backend.oee.application.parser.ParseResult;
import com.industrialhub.backend.oee.domain.ImportBatch;
import com.industrialhub.backend.oee.infrastructure.ImportBatchRepository;
import com.industrialhub.backend.oee.infrastructure.TimeRecordRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ImportDynamicsExcelUseCaseTest {

    @Mock private DynamicsExcelParser parser;
    @Mock private ImportBatchRepository batchRepository;
    @Mock private TimeRecordRepository timeRecordRepository;
    @Mock private com.industrialhub.backend.common.application.AuditService auditService;
    @Mock private MultipartFile file;

    @InjectMocks
    private ImportDynamicsExcelUseCase useCase;

    private static final LocalDate PERIOD = LocalDate.of(2026, 4, 28);

    @Test
    void noDuplicate_importSucceeds_replacedFalse() throws IOException {
        stubFile();
        stubParser(PERIOD);
        when(batchRepository.findByPeriodDate(PERIOD)).thenReturn(Optional.empty());
        stubBatchSave();

        ImportResultDto result = useCase.execute(file, false, "system");

        assertThat(result.replaced()).isFalse();
        assertThat(result.periodDate()).isEqualTo(PERIOD);
        verify(batchRepository, never()).delete(any());
        verify(timeRecordRepository, never()).deleteAllByBatch(any());
    }

    @Test
    void duplicateWithoutOverwrite_throws409() throws IOException {
        stubFile();
        stubParser(PERIOD);
        ImportBatch existing = existingBatch();
        when(batchRepository.findByPeriodDate(PERIOD)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> useCase.execute(file, false, "system"))
                .isInstanceOf(DuplicateImportException.class);

        verify(batchRepository, never()).delete(any());
    }

    @Test
    void duplicateWithOverwrite_deletesOldAndReimports_replacedTrue() throws IOException {
        stubFile();
        stubParser(PERIOD);
        ImportBatch existing = existingBatch();
        when(batchRepository.findByPeriodDate(PERIOD)).thenReturn(Optional.of(existing));
        stubBatchSave();

        ImportResultDto result = useCase.execute(file, true, "system");

        assertThat(result.replaced()).isTrue();
        verify(timeRecordRepository).deleteAllByBatch(existing);
        verify(batchRepository).delete(existing);
        verify(batchRepository).flush();
    }

    @Test
    void overwrite_noExistingBatch_replacedFalse() throws IOException {
        stubFile();
        stubParser(PERIOD);
        when(batchRepository.findByPeriodDate(PERIOD)).thenReturn(Optional.empty());
        stubBatchSave();

        ImportResultDto result = useCase.execute(file, true, "system");

        assertThat(result.replaced()).isFalse();
        verify(batchRepository, never()).delete(any());
    }

    // --- helpers ---

    private void stubFile() throws IOException {
        when(file.getOriginalFilename()).thenReturn("export.xlsx");
        when(file.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[0]));
    }

    private void stubParser(LocalDate period) throws IOException {
        ParseResult parsed = new ParseResult(period, 0, List.of(), 0, List.of());
        when(parser.parse(any(), anyString())).thenReturn(parsed);
    }

    private void stubBatchSave() {
        when(batchRepository.save(any())).thenAnswer(inv -> {
            ImportBatch b = inv.getArgument(0);
            b.setId(UUID.randomUUID());
            return b;
        });
    }

    private ImportBatch existingBatch() {
        return ImportBatch.builder()
                .id(UUID.randomUUID())
                .periodDate(PERIOD)
                .fileName("old.xlsx")
                .workerCount(5)
                .totalRecords(100)
                .build();
    }
}
