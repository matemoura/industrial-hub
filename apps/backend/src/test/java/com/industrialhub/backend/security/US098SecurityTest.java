package com.industrialhub.backend.security;

import com.industrialhub.backend.common.application.AuditService;
import com.industrialhub.backend.production.application.dto.ImportProductionBatchResponse;
import com.industrialhub.backend.production.application.usecase.ExcelFileValidator;
import com.industrialhub.backend.production.application.usecase.ImportProductCatalogUseCase;
import com.industrialhub.backend.production.domain.ImportProductionBatch;
import com.industrialhub.backend.production.domain.ProductionImportType;
import com.industrialhub.backend.production.infrastructure.ImportProductionBatchRepository;
import com.industrialhub.backend.production.infrastructure.ProductFamilyRepository;
import com.industrialhub.backend.production.infrastructure.ProductRepository;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * US-098 / ADR-041 Decisão 7 — security regression tests for SEC-107, SEC-108, SEC-109.
 *
 * <ul>
 *   <li>SEC-107: non-Excel files are rejected by magic-byte detection (Tika)</li>
 *   <li>SEC-108: generic error message in catch(Exception) — no stack trace or DB info exposed</li>
 *   <li>SEC-109: importedBy field removed from ImportProductionBatchResponse</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class US098SecurityTest {

    @Mock private ProductRepository productRepository;
    @Mock private ProductFamilyRepository familyRepository;
    @Mock private ImportProductionBatchRepository batchRepository;
    @Mock private AuditService auditService;

    private ImportProductCatalogUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ImportProductCatalogUseCase(productRepository, familyRepository, batchRepository, auditService);
    }

    // -----------------------------------------------------------------------
    // SEC-107: Tika MIME validation — arquivo não-Excel é rejeitado
    // -----------------------------------------------------------------------

    @Test
    void sec107_shouldReject_whenFileIsNotExcel() {
        // Arrange — a plain text file disguised as .xlsx
        byte[] pdfBytes = "%PDF-1.4 fake pdf content".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile fakePdf = new MockMultipartFile(
                "file", "malicious.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                pdfBytes);

        // Act & Assert — ExcelFileValidator.validate() should reject
        assertThatThrownBy(() -> ExcelFileValidator.validate(fakePdf))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("apenas Excel");
    }

    @Test
    void sec107_shouldAccept_validXlsxFile() throws Exception {
        MockMultipartFile validFile = buildValidExcel();

        // Should not throw
        assertThatNoException().isThrownBy(() -> ExcelFileValidator.validate(validFile));
    }

    // -----------------------------------------------------------------------
    // SEC-108: Generic message for unexpected exceptions — no DB info exposed
    // -----------------------------------------------------------------------

    @Test
    void sec108_shouldReturnGenericMessage_whenUnexpectedException() throws Exception {
        // Arrange — valid Excel, but productRepository.findByDynamicsCode throws a RuntimeException
        // simulating a Hibernate error with potential DB info
        MockMultipartFile file = buildExcel("P001|Produto A|FINISHED|FAM1|Familia 1|un|false");

        when(familyRepository.findByCode(any())).thenReturn(java.util.Optional.empty());
        when(familyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(productRepository.findByDynamicsCode(any()))
                .thenThrow(new RuntimeException(
                        "column 'family_id' cannot be null — check constraint violations in table 'product'"));

        when(batchRepository.save(any())).thenAnswer(inv -> {
            ImportProductionBatch b = inv.getArgument(0);
            return ImportProductionBatch.builder()
                    .id(UUID.randomUUID()).type(b.getType()).importedAt(b.getImportedAt())
                    .importedBy(b.getImportedBy()).totalRecords(b.getTotalRecords())
                    .createdRecords(b.getCreatedRecords()).updatedRecords(b.getUpdatedRecords())
                    .errorRecords(b.getErrorRecords()).build();
        });

        // Act
        ImportProductionBatchResponse result = useCase.execute(file, "admin");

        // Assert — error message must NOT contain DB info
        assertThat(result.errors()).hasSize(1);
        String errorMsg = result.errors().get(0).message();
        assertThat(errorMsg).doesNotContain("family_id");
        assertThat(errorMsg).doesNotContain("constraint");
        assertThat(errorMsg).doesNotContain("table");
        assertThat(errorMsg).contains("Erro ao processar linha");
    }

    // -----------------------------------------------------------------------
    // SEC-109: importedBy removed from response — field must not exist
    // -----------------------------------------------------------------------

    @Test
    void sec109_importedByField_shouldNotBePresentInResponse() {
        // Verify at the record component level — no importedBy component in the record
        java.lang.reflect.RecordComponent[] components =
                ImportProductionBatchResponse.class.getRecordComponents();

        boolean hasImportedBy = java.util.Arrays.stream(components)
                .anyMatch(c -> c.getName().equals("importedBy"));

        assertThat(hasImportedBy)
                .as("ImportProductionBatchResponse must NOT have importedBy field (SEC-109 / ADR-041)")
                .isFalse();
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private MockMultipartFile buildValidExcel() throws Exception {
        return buildExcel("PTEST|Produto Teste|FINISHED|FAM1|Familia Teste|un|false");
    }

    private MockMultipartFile buildExcel(String... rows) throws Exception {
        Workbook wb = new XSSFWorkbook();
        Sheet sheet = wb.createSheet();
        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("dynamics_code");
        header.createCell(1).setCellValue("name");
        header.createCell(2).setCellValue("type");
        header.createCell(3).setCellValue("family_code");
        header.createCell(4).setCellValue("family_name");
        header.createCell(5).setCellValue("unit");
        header.createCell(6).setCellValue("requires_sterilization");

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
        return new MockMultipartFile("file", "test.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                out.toByteArray());
    }
}
