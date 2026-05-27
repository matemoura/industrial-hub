package com.industrialhub.backend.production;

import com.industrialhub.backend.common.application.AuditService;
import com.industrialhub.backend.production.application.dto.ImportProductionBatchResponse;
import com.industrialhub.backend.production.application.usecase.ImportLeadTimesUseCase;
import com.industrialhub.backend.production.domain.*;
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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * AC#6 — ImportLeadTimesUseCase unit tests (Maiana, Sprint 29).
 */
@ExtendWith(MockitoExtension.class)
class ImportLeadTimesUseCaseTest {

    @Mock private ProductRepository productRepository;
    @Mock private ImportProductionBatchRepository batchRepository;
    @Mock private AuditService auditService;

    private ImportLeadTimesUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ImportLeadTimesUseCase(productRepository, batchRepository, auditService);
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /**
     * Builds a valid .xlsx MultipartFile with header row and the given data rows.
     * Each row is a pipe-separated string: "dynamics_code|lead_time_days|min_stock_qty|batch_size"
     * An empty token ("") leaves that cell blank (simulates null column).
     */
    private MockMultipartFile buildExcel(String... rows) throws Exception {
        Workbook wb = new XSSFWorkbook();
        Sheet sheet = wb.createSheet();

        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("dynamics_code");
        header.createCell(1).setCellValue("lead_time_days");
        header.createCell(2).setCellValue("min_stock_qty");
        header.createCell(3).setCellValue("batch_size");

        for (int i = 0; i < rows.length; i++) {
            String[] cols = rows[i].split("\\|", -1);
            Row row = sheet.createRow(i + 1);
            for (int j = 0; j < cols.length; j++) {
                if (!cols[j].isEmpty()) {
                    row.createCell(j).setCellValue(cols[j]);
                }
                // empty string → no cell created → ExcelParsingHelper.getInteger returns null
            }
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        wb.write(out);
        wb.close();
        return new MockMultipartFile(
            "file", "lead_times.xlsx",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            out.toByteArray());
    }

    private void stubBatchSave() {
        when(batchRepository.save(any())).thenAnswer(inv -> {
            ImportProductionBatch b = inv.getArgument(0);
            return ImportProductionBatch.builder()
                .id(UUID.randomUUID())
                .type(b.getType())
                .importedAt(b.getImportedAt())
                .importedBy(b.getImportedBy())
                .totalRecords(b.getTotalRecords())
                .createdRecords(b.getCreatedRecords())
                .updatedRecords(b.getUpdatedRecords())
                .errorRecords(b.getErrorRecords())
                .build();
        });
    }

    // -----------------------------------------------------------------------
    // AC#6-1: produto existente, linha válida → campos atualizados, updatedRecords=1
    // -----------------------------------------------------------------------
    @Test
    void shouldUpdateLeadTimeFields_whenProductExistsAndRowIsValid() throws Exception {
        // Arrange
        MockMultipartFile file = buildExcel("P100|10|50|25");

        UUID productId = UUID.randomUUID();
        Product product = Product.builder()
            .id(productId)
            .dynamicsCode("P100")
            .name("Produto Lead")
            .type(ProductType.FINISHED)
            .active(true)
            .leadTimeDays(5)
            .minStockQty(20)
            .batchSize(10)
            .build();

        when(productRepository.findByDynamicsCode("P100")).thenReturn(Optional.of(product));
        when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        stubBatchSave();

        // Act
        ImportProductionBatchResponse result = useCase.execute(file, "admin");

        // Assert
        assertThat(result.updatedRecords()).isEqualTo(1);
        assertThat(result.errorRecords()).isEqualTo(0);
        assertThat(product.getLeadTimeDays()).isEqualTo(10);
        assertThat(product.getMinStockQty()).isEqualTo(50);
        assertThat(product.getBatchSize()).isEqualTo(25);
        verify(productRepository).save(product);
    }

    // -----------------------------------------------------------------------
    // AC#6-2: coluna nula na planilha → campo NÃO sobrescrito (preservado)
    // -----------------------------------------------------------------------
    @Test
    void shouldPreserveExistingField_whenSpreadsheetColumnIsNull() throws Exception {
        // Arrange — only lead_time_days is provided; min_stock_qty and batch_size are blank
        MockMultipartFile file = buildExcel("P101|7||");

        UUID productId = UUID.randomUUID();
        Product product = Product.builder()
            .id(productId)
            .dynamicsCode("P101")
            .name("Produto Parcial")
            .type(ProductType.FINISHED)
            .active(true)
            .leadTimeDays(3)
            .minStockQty(99)
            .batchSize(42)
            .build();

        when(productRepository.findByDynamicsCode("P101")).thenReturn(Optional.of(product));
        when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        stubBatchSave();

        // Act
        ImportProductionBatchResponse result = useCase.execute(file, "admin");

        // Assert
        assertThat(result.updatedRecords()).isEqualTo(1);
        assertThat(result.errorRecords()).isEqualTo(0);
        // leadTimeDays updated because column was provided
        assertThat(product.getLeadTimeDays()).isEqualTo(7);
        // minStockQty and batchSize must be PRESERVED — null columns must not overwrite
        assertThat(product.getMinStockQty()).isEqualTo(99);
        assertThat(product.getBatchSize()).isEqualTo(42);
    }

    // -----------------------------------------------------------------------
    // AC#6-3: dynamics_code desconhecido → errorRecords=1, produto não criado
    // -----------------------------------------------------------------------
    @Test
    void shouldRecordError_whenDynamicsCodeNotFound() throws Exception {
        // Arrange
        MockMultipartFile file = buildExcel("GHOST_CODE|5|10|5");

        when(productRepository.findByDynamicsCode("GHOST_CODE")).thenReturn(Optional.empty());
        stubBatchSave();

        // Act
        ImportProductionBatchResponse result = useCase.execute(file, "admin");

        // Assert
        assertThat(result.errorRecords()).isEqualTo(1);
        assertThat(result.updatedRecords()).isEqualTo(0);
        assertThat(result.errors()).hasSize(1);
        assertThat(result.errors().get(0).message()).contains("GHOST_CODE");
        // product must NOT be saved / created
        verify(productRepository, never()).save(any());
    }

    // -----------------------------------------------------------------------
    // AC#6-4: lead_time_days < 0 → errorRecords=1, produto não alterado
    // -----------------------------------------------------------------------
    @Test
    void shouldRecordError_whenLeadTimeDaysIsNegative() throws Exception {
        // Arrange
        MockMultipartFile file = buildExcel("P102|-3|10|5");

        stubBatchSave();

        // Act
        ImportProductionBatchResponse result = useCase.execute(file, "admin");

        // Assert
        assertThat(result.errorRecords()).isEqualTo(1);
        assertThat(result.updatedRecords()).isEqualTo(0);
        assertThat(result.errors().get(0).message()).containsIgnoringCase("negativo");
        // No product lookup should have happened after the validation error
        verify(productRepository, never()).findByDynamicsCode(anyString());
        verify(productRepository, never()).save(any());
    }
}
