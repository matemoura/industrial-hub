package com.industrialhub.backend.production;

import com.industrialhub.backend.common.application.AuditService;
import com.industrialhub.backend.production.application.dto.ImportProductionBatchResponse;
import com.industrialhub.backend.production.application.usecase.ImportStockSnapshotUseCase;
import com.industrialhub.backend.production.domain.*;
import com.industrialhub.backend.production.infrastructure.ImportProductionBatchRepository;
import com.industrialhub.backend.production.infrastructure.ProductRepository;
import com.industrialhub.backend.production.infrastructure.StockSnapshotRepository;
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
class ImportStockSnapshotUseCaseTest {

    @Mock private ProductRepository productRepository;
    @Mock private StockSnapshotRepository stockSnapshotRepository;
    @Mock private ImportProductionBatchRepository batchRepository;
    @Mock private AuditService auditService;

    private ImportStockSnapshotUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ImportStockSnapshotUseCase(productRepository, stockSnapshotRepository, batchRepository, auditService);
    }

    private MockMultipartFile buildExcel(String... rows) throws Exception {
        Workbook wb = new XSSFWorkbook();
        Sheet sheet = wb.createSheet();
        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("dynamics_code");
        header.createCell(1).setCellValue("qty");
        header.createCell(2).setCellValue("snapshot_date");

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
        return new MockMultipartFile("file", "stock.xlsx",
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
    void shouldCreateSnapshot_whenProductAndDateAreNew() throws Exception {
        // Arrange
        MockMultipartFile file = buildExcel("P001|50|2024-05-01");

        Product product = Product.builder().id(UUID.randomUUID()).dynamicsCode("P001").name("Produto A")
                .type(ProductType.FINISHED).active(true).build();
        when(productRepository.findByDynamicsCode("P001")).thenReturn(Optional.of(product));
        when(stockSnapshotRepository.findByProductIdAndSnapshotDate(product.getId(), LocalDate.of(2024, 5, 1)))
                .thenReturn(Optional.empty());
        when(stockSnapshotRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        stubBatchSave();

        // Act
        ImportProductionBatchResponse result = useCase.execute(file, "admin");

        // Assert
        assertThat(result.createdRecords()).isEqualTo(1);
        assertThat(result.updatedRecords()).isEqualTo(0);
        assertThat(result.errorRecords()).isEqualTo(0);
        verify(stockSnapshotRepository).save(any(StockSnapshot.class));
    }

    @Test
    void shouldUpdateQty_whenSameDateAlreadyExists() throws Exception {
        // Arrange
        MockMultipartFile file = buildExcel("P002|75|2024-05-01");

        UUID productId = UUID.randomUUID();
        Product product = Product.builder().id(productId).dynamicsCode("P002").name("Produto B")
                .type(ProductType.FINISHED).active(true).build();
        StockSnapshot existing = StockSnapshot.builder().id(UUID.randomUUID()).product(product)
                .qty(30).snapshotDate(LocalDate.of(2024, 5, 1)).importedAt(LocalDateTime.now()).build();

        when(productRepository.findByDynamicsCode("P002")).thenReturn(Optional.of(product));
        when(stockSnapshotRepository.findByProductIdAndSnapshotDate(productId, LocalDate.of(2024, 5, 1)))
                .thenReturn(Optional.of(existing));
        when(stockSnapshotRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        stubBatchSave();

        // Act
        ImportProductionBatchResponse result = useCase.execute(file, "admin");

        // Assert
        assertThat(result.createdRecords()).isEqualTo(0);
        assertThat(result.updatedRecords()).isEqualTo(1);
        assertThat(existing.getQty()).isEqualTo(75); // qty was updated in place
    }

    @Test
    void shouldCreateSecondSnapshot_whenDifferentDate() throws Exception {
        // Arrange — same product, different date (history preserved)
        MockMultipartFile file = buildExcel(
                "P003|100|2024-05-01",
                "P003|120|2024-05-08"
        );

        UUID productId = UUID.randomUUID();
        Product product = Product.builder().id(productId).dynamicsCode("P003").name("Produto C")
                .type(ProductType.FINISHED).active(true).build();

        when(productRepository.findByDynamicsCode("P003")).thenReturn(Optional.of(product));
        // First date → new
        when(stockSnapshotRepository.findByProductIdAndSnapshotDate(productId, LocalDate.of(2024, 5, 1)))
                .thenReturn(Optional.empty());
        // Second date → also new (different date key)
        when(stockSnapshotRepository.findByProductIdAndSnapshotDate(productId, LocalDate.of(2024, 5, 8)))
                .thenReturn(Optional.empty());
        when(stockSnapshotRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        stubBatchSave();

        // Act
        ImportProductionBatchResponse result = useCase.execute(file, "admin");

        // Assert
        assertThat(result.createdRecords()).isEqualTo(2); // two distinct snapshots created
        assertThat(result.updatedRecords()).isEqualTo(0);
        verify(stockSnapshotRepository, times(2)).save(any(StockSnapshot.class));
    }

    @Test
    void shouldSkipRow_whenDynamicsCodeNotFound() throws Exception {
        // Arrange
        MockMultipartFile file = buildExcel("UNKNOWN|10|2024-05-01");

        when(productRepository.findByDynamicsCode("UNKNOWN")).thenReturn(Optional.empty());
        stubBatchSave();

        // Act
        ImportProductionBatchResponse result = useCase.execute(file, "admin");

        // Assert
        assertThat(result.errorRecords()).isEqualTo(1);
        assertThat(result.createdRecords()).isEqualTo(0);
        assertThat(result.errors()).hasSize(1);
        assertThat(result.errors().get(0).message()).contains("UNKNOWN");
        verify(stockSnapshotRepository, never()).save(any());
    }
}
