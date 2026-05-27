package com.industrialhub.backend.production;

import com.industrialhub.backend.common.application.AuditService;
import com.industrialhub.backend.production.application.dto.ImportProductionBatchResponse;
import com.industrialhub.backend.production.application.usecase.ImportCycleTimesUseCase;
import com.industrialhub.backend.production.domain.*;
import com.industrialhub.backend.production.infrastructure.CycleTimeRepository;
import com.industrialhub.backend.production.infrastructure.ImportProductionBatchRepository;
import com.industrialhub.backend.production.infrastructure.ProductRepository;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ImportCycleTimesUseCaseTest {

    @Mock private ProductRepository productRepository;
    @Mock private CycleTimeRepository cycleTimeRepository;
    @Mock private ImportProductionBatchRepository batchRepository;
    @Mock private AuditService auditService;

    private ImportCycleTimesUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ImportCycleTimesUseCase(productRepository, cycleTimeRepository, batchRepository, auditService);
    }

    private MockMultipartFile buildExcel(String... rows) throws Exception {
        Workbook wb = new XSSFWorkbook();
        Sheet sheet = wb.createSheet();
        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("dynamics_code");
        header.createCell(1).setCellValue("seconds_per_unit");
        header.createCell(2).setCellValue("effective_date");

        for (int i = 0; i < rows.length; i++) {
            String[] cols = rows[i].split("\\|", -1);
            Row row = sheet.createRow(i + 1);
            for (int j = 0; j < cols.length; j++) {
                row.createCell(j).setCellValue(cols[j]);
            }
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        wb.write(out);
        wb.close();
        return new MockMultipartFile("file", "cycle.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                out.toByteArray());
    }

    private void stubBatchSave() {
        when(batchRepository.save(any())).thenAnswer(inv -> {
            ImportProductionBatch b = inv.getArgument(0);
            return ImportProductionBatch.builder()
                    .id(UUID.randomUUID()).type(b.getType())
                    .importedAt(b.getImportedAt()).importedBy(b.getImportedBy())
                    .totalRecords(b.getTotalRecords()).createdRecords(b.getCreatedRecords())
                    .updatedRecords(b.getUpdatedRecords()).errorRecords(b.getErrorRecords())
                    .build();
        });
    }

    @Test
    void shouldCreateNewVersion_whenEffectiveDateIsNew() throws Exception {
        // Arrange
        MockMultipartFile file = buildExcel("P001|45.5|2024-06-01");

        UUID productId = UUID.randomUUID();
        Product product = Product.builder().id(productId).dynamicsCode("P001")
                .name("Produto A").type(ProductType.FINISHED).active(true).build();

        when(productRepository.findByDynamicsCode("P001")).thenReturn(Optional.of(product));
        when(cycleTimeRepository.findByProductIdAndEffectiveDate(productId, LocalDate.of(2024, 6, 1)))
                .thenReturn(Optional.empty());
        when(cycleTimeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        stubBatchSave();

        // Act
        ImportProductionBatchResponse result = useCase.execute(file, "admin");

        // Assert
        assertThat(result.createdRecords()).isEqualTo(1);
        assertThat(result.updatedRecords()).isEqualTo(0);
        verify(cycleTimeRepository).save(any(CycleTime.class));
    }

    @Test
    void shouldUpdateSecondsPerUnit_whenSameEffectiveDateExists() throws Exception {
        // Arrange
        MockMultipartFile file = buildExcel("P002|60.0|2024-06-01");

        UUID productId = UUID.randomUUID();
        Product product = Product.builder().id(productId).dynamicsCode("P002")
                .name("Produto B").type(ProductType.FINISHED).active(true).build();
        CycleTime existing = CycleTime.builder().id(UUID.randomUUID()).product(product)
                .secondsPerUnit(45.0).effectiveDate(LocalDate.of(2024, 6, 1))
                .importedBy("old-user").importedAt(LocalDateTime.now()).build();

        when(productRepository.findByDynamicsCode("P002")).thenReturn(Optional.of(product));
        when(cycleTimeRepository.findByProductIdAndEffectiveDate(productId, LocalDate.of(2024, 6, 1)))
                .thenReturn(Optional.of(existing));
        when(cycleTimeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        stubBatchSave();

        // Act
        ImportProductionBatchResponse result = useCase.execute(file, "admin");

        // Assert
        assertThat(result.updatedRecords()).isEqualTo(1);
        assertThat(result.createdRecords()).isEqualTo(0);
        assertThat(existing.getSecondsPerUnit()).isEqualTo(60.0); // updated in place
    }

    @Test
    void shouldCreateTwoVersions_withDistinctEffectiveDates() throws Exception {
        // Arrange — different effective dates → separate records, history preserved
        MockMultipartFile file = buildExcel(
                "P003|30.0|2024-01-01",
                "P003|35.0|2024-07-01"
        );

        UUID productId = UUID.randomUUID();
        Product product = Product.builder().id(productId).dynamicsCode("P003")
                .name("Produto C").type(ProductType.FINISHED).active(true).build();

        when(productRepository.findByDynamicsCode("P003")).thenReturn(Optional.of(product));
        when(cycleTimeRepository.findByProductIdAndEffectiveDate(productId, LocalDate.of(2024, 1, 1)))
                .thenReturn(Optional.empty());
        when(cycleTimeRepository.findByProductIdAndEffectiveDate(productId, LocalDate.of(2024, 7, 1)))
                .thenReturn(Optional.empty());
        when(cycleTimeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        stubBatchSave();

        // Act
        ImportProductionBatchResponse result = useCase.execute(file, "admin");

        // Assert
        assertThat(result.createdRecords()).isEqualTo(2); // two versions, not overwriting each other
        assertThat(result.updatedRecords()).isEqualTo(0);
        verify(cycleTimeRepository, times(2)).save(any(CycleTime.class));
    }

    @Test
    void shouldSkipRow_whenProductNotFound() throws Exception {
        // Arrange
        MockMultipartFile file = buildExcel("GHOST|20.0|2024-06-01");

        when(productRepository.findByDynamicsCode("GHOST")).thenReturn(Optional.empty());
        stubBatchSave();

        // Act
        ImportProductionBatchResponse result = useCase.execute(file, "admin");

        // Assert
        assertThat(result.errorRecords()).isEqualTo(1);
        assertThat(result.createdRecords()).isEqualTo(0);
        assertThat(result.errors()).hasSize(1);
        assertThat(result.errors().get(0).message()).contains("GHOST");
        verify(cycleTimeRepository, never()).save(any());
    }
}
