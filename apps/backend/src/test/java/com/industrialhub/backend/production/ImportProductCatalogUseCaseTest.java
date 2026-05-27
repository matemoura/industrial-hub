package com.industrialhub.backend.production;

import com.industrialhub.backend.common.application.AuditService;
import com.industrialhub.backend.common.domain.AuditAction;
import com.industrialhub.backend.production.application.dto.ImportProductionBatchResponse;
import com.industrialhub.backend.production.application.usecase.ImportProductCatalogUseCase;
import com.industrialhub.backend.production.domain.*;
import com.industrialhub.backend.production.infrastructure.ImportProductionBatchRepository;
import com.industrialhub.backend.production.infrastructure.ProductFamilyRepository;
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
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ImportProductCatalogUseCaseTest {

    @Mock
    private ProductRepository productRepository;
    @Mock
    private ProductFamilyRepository familyRepository;
    @Mock
    private ImportProductionBatchRepository batchRepository;
    @Mock
    private AuditService auditService;

    private ImportProductCatalogUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ImportProductCatalogUseCase(productRepository, familyRepository, batchRepository, auditService);
    }

    private MockMultipartFile buildExcel(String... rows) throws Exception {
        Workbook wb = new XSSFWorkbook();
        Sheet sheet = wb.createSheet();
        // Header
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

    private ImportProductionBatch stubbedBatch(int created, int updated, int errors) {
        return ImportProductionBatch.builder()
                .id(UUID.randomUUID())
                .type(ProductionImportType.PRODUCT_CATALOG)
                .importedAt(LocalDateTime.now())
                .importedBy("admin")
                .totalRecords(created + updated + errors)
                .createdRecords(created)
                .updatedRecords(updated)
                .errorRecords(errors)
                .build();
    }

    @Test
    void shouldCreate2Products_andUpdate1() throws Exception {
        // Arrange — 2 new products, 1 existing
        MockMultipartFile file = buildExcel(
                "P001|Produto A|FINISHED|FAM1|Familia 1|un|false",
                "P002|Produto B|INTERMEDIATE|FAM1|Familia 1|cx|false",
                "P003|Produto C|FINISHED|FAM1|Familia 1|un|true"
        );

        ProductFamily family = ProductFamily.builder().id(UUID.randomUUID()).code("FAM1").name("Familia 1").build();
        when(familyRepository.findByCode("FAM1")).thenReturn(Optional.of(family));

        when(productRepository.findByDynamicsCode("P001")).thenReturn(Optional.empty());
        when(productRepository.findByDynamicsCode("P002")).thenReturn(Optional.empty());
        when(productRepository.findByDynamicsCode("P003")).thenReturn(Optional.of(
                Product.builder().id(UUID.randomUUID()).dynamicsCode("P003")
                        .name("Old Name").type(ProductType.FINISHED).active(true).build()
        ));

        when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(batchRepository.save(any())).thenAnswer(inv -> {
            ImportProductionBatch b = inv.getArgument(0);
            b = ImportProductionBatch.builder()
                    .id(UUID.randomUUID()).type(b.getType()).importedAt(b.getImportedAt())
                    .importedBy(b.getImportedBy()).totalRecords(b.getTotalRecords())
                    .createdRecords(b.getCreatedRecords()).updatedRecords(b.getUpdatedRecords())
                    .errorRecords(b.getErrorRecords()).build();
            return b;
        });

        // Act
        ImportProductionBatchResponse result = useCase.execute(file, "admin");

        // Assert
        assertThat(result.createdRecords()).isEqualTo(2);
        assertThat(result.updatedRecords()).isEqualTo(1);
        assertThat(result.errorRecords()).isEqualTo(0);
        assertThat(result.errors()).isEmpty();
        verify(auditService).log(eq("admin"), eq(AuditAction.PRODUCTION_IMPORT), anyString(), anyString(), any());
    }

    @Test
    void shouldCreateFamily_whenFamilyCodeIsNew() throws Exception {
        // Arrange
        MockMultipartFile file = buildExcel("P010|Produto X|FINISHED|NEW_FAM|Nova Familia|un|false");

        ProductFamily newFamily = ProductFamily.builder().id(UUID.randomUUID()).code("NEW_FAM").name("Nova Familia").build();
        when(familyRepository.findByCode("NEW_FAM")).thenReturn(Optional.empty());
        when(familyRepository.save(any())).thenReturn(newFamily);
        when(productRepository.findByDynamicsCode("P010")).thenReturn(Optional.empty());
        when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(batchRepository.save(any())).thenAnswer(inv -> {
            ImportProductionBatch b = inv.getArgument(0);
            return ImportProductionBatch.builder().id(UUID.randomUUID()).type(b.getType())
                    .importedAt(b.getImportedAt()).importedBy(b.getImportedBy())
                    .totalRecords(1).createdRecords(1).updatedRecords(0).errorRecords(0).build();
        });

        // Act
        ImportProductionBatchResponse result = useCase.execute(file, "admin");

        // Assert
        assertThat(result.createdRecords()).isEqualTo(1);
        verify(familyRepository).save(any(ProductFamily.class));
    }

    @Test
    void shouldSkipInvalidTypeLine_andProcessValidLine() throws Exception {
        // Arrange
        MockMultipartFile file = buildExcel(
                "P020|Produto OK|FINISHED|FAM1|Familia 1|un|false",
                "P021|Produto BAD|INVALID_TYPE|FAM1|Familia 1|un|false"
        );

        ProductFamily family = ProductFamily.builder().id(UUID.randomUUID()).code("FAM1").name("Familia 1").build();
        when(familyRepository.findByCode("FAM1")).thenReturn(Optional.of(family));
        when(productRepository.findByDynamicsCode("P020")).thenReturn(Optional.empty());
        when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(batchRepository.save(any())).thenAnswer(inv -> {
            ImportProductionBatch b = inv.getArgument(0);
            return ImportProductionBatch.builder().id(UUID.randomUUID()).type(b.getType())
                    .importedAt(b.getImportedAt()).importedBy(b.getImportedBy())
                    .totalRecords(b.getTotalRecords()).createdRecords(b.getCreatedRecords())
                    .updatedRecords(b.getUpdatedRecords()).errorRecords(b.getErrorRecords()).build();
        });

        // Act
        ImportProductionBatchResponse result = useCase.execute(file, "admin");

        // Assert
        assertThat(result.createdRecords()).isEqualTo(1);
        assertThat(result.errorRecords()).isEqualTo(1);
        assertThat(result.errors()).hasSize(1);
        assertThat(result.errors().get(0).message()).contains("INVALID_TYPE");
    }

    @Test
    void shouldNotOverwriteHubManagedFields_onUpdate() throws Exception {
        // Arrange
        MockMultipartFile file = buildExcel("P030|Updated Name|FINISHED|FAM1|Familia 1|un|false");

        ProductFamily family = ProductFamily.builder().id(UUID.randomUUID()).code("FAM1").name("Familia 1").build();
        when(familyRepository.findByCode("FAM1")).thenReturn(Optional.of(family));

        Product existing = Product.builder()
                .id(UUID.randomUUID())
                .dynamicsCode("P030")
                .name("Old Name")
                .type(ProductType.FINISHED)
                .leadTimeDays(10)     // Hub-managed — must be preserved
                .minStockQty(100)     // Hub-managed — must be preserved
                .batchSize(50)        // Hub-managed — must be preserved
                .active(true)
                .build();
        when(productRepository.findByDynamicsCode("P030")).thenReturn(Optional.of(existing));

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        when(productRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));
        when(batchRepository.save(any())).thenAnswer(inv -> {
            ImportProductionBatch b = inv.getArgument(0);
            return ImportProductionBatch.builder().id(UUID.randomUUID()).type(b.getType())
                    .importedAt(b.getImportedAt()).importedBy(b.getImportedBy())
                    .totalRecords(1).createdRecords(0).updatedRecords(1).errorRecords(0).build();
        });

        // Act
        ImportProductionBatchResponse result = useCase.execute(file, "admin");

        // Assert
        assertThat(result.updatedRecords()).isEqualTo(1);
        Product saved = captor.getValue();
        assertThat(saved.getLeadTimeDays()).isEqualTo(10);   // preserved
        assertThat(saved.getMinStockQty()).isEqualTo(100);   // preserved
        assertThat(saved.getBatchSize()).isEqualTo(50);       // preserved
        assertThat(saved.getName()).isEqualTo("Updated Name"); // updated from dynamics
    }
}
