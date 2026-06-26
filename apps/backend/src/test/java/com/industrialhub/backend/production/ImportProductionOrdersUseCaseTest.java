package com.industrialhub.backend.production;

import com.industrialhub.backend.common.application.AuditService;
import com.industrialhub.backend.production.application.dto.ImportProductionBatchResponse;
import com.industrialhub.backend.production.application.usecase.GetStaffingConfigUseCase;
import com.industrialhub.backend.production.application.usecase.ImportProductionOrdersUseCase;
import com.industrialhub.backend.production.domain.*;
import com.industrialhub.backend.production.infrastructure.CycleTimeRepository;
import com.industrialhub.backend.production.infrastructure.ImportProductionBatchRepository;
import com.industrialhub.backend.production.infrastructure.ProductRepository;
import com.industrialhub.backend.production.infrastructure.ProductionOrderRepository;
import com.industrialhub.backend.production.infrastructure.SterilizationLoadRepository;
import com.industrialhub.backend.production.domain.CycleTime;
import com.industrialhub.backend.production.domain.StaffingConfig;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ImportProductionOrdersUseCaseTest {

    @Mock private ProductRepository productRepository;
    @Mock private ProductionOrderRepository orderRepository;
    @Mock private ImportProductionBatchRepository batchRepository;
    @Mock private AuditService auditService;
    @Mock private CycleTimeRepository cycleTimeRepository;
    @Mock private GetStaffingConfigUseCase getStaffingConfig;
    @Mock private SterilizationLoadRepository loadRepository;

    private ImportProductionOrdersUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ImportProductionOrdersUseCase(
                productRepository, orderRepository, batchRepository, auditService,
                cycleTimeRepository, getStaffingConfig, loadRepository);
    }

    private MockMultipartFile buildExcel(String... rows) throws Exception {
        Workbook wb = new XSSFWorkbook();
        Sheet sheet = wb.createSheet();
        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("op_number");
        header.createCell(1).setCellValue("dynamics_code");
        header.createCell(2).setCellValue("status");
        header.createCell(3).setCellValue("planned_qty");
        header.createCell(4).setCellValue("produced_qty");
        header.createCell(5).setCellValue("start_date");
        header.createCell(6).setCellValue("due_date");

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
        return new MockMultipartFile("file", "orders.xlsx",
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
    void shouldCreateOrder_whenDynamicsOrderNumberIsNew() throws Exception {
        // Arrange
        MockMultipartFile file = buildExcel("OP-001|P001|PLANNED|100|0|2024-05-01|2024-05-15");

        Product product = Product.builder().id(UUID.randomUUID()).dynamicsCode("P001")
                .name("Produto A").type(ProductType.FINISHED).active(true).build();
        when(productRepository.findByDynamicsCode("P001")).thenReturn(Optional.of(product));
        when(orderRepository.findByDynamicsOrderNumber("OP-001")).thenReturn(Optional.empty());
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        stubBatchSave();

        // Act
        ImportProductionBatchResponse result = useCase.execute(file, "admin");

        // Assert
        assertThat(result.createdRecords()).isEqualTo(1);
        assertThat(result.updatedRecords()).isEqualTo(0);
        assertThat(result.errorRecords()).isEqualTo(0);
        verify(orderRepository).save(any(ProductionOrder.class));
    }

    @Test
    void shouldUpdateOrder_whenDynamicsOrderNumberExists() throws Exception {
        // Arrange
        MockMultipartFile file = buildExcel("OP-002|P002|IN_PROGRESS|200|80|2024-05-01|2024-05-20");

        UUID productId = UUID.randomUUID();
        Product product = Product.builder().id(productId).dynamicsCode("P002")
                .name("Produto B").type(ProductType.FINISHED).active(true).build();
        ProductionOrder existing = ProductionOrder.builder()
                .id(UUID.randomUUID()).dynamicsOrderNumber("OP-002").product(product)
                .status(ProductionOrderStatus.PLANNED)
                .plannedQty(BigDecimal.valueOf(200)).producedQty(BigDecimal.ZERO)
                .importedAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build();

        when(productRepository.findByDynamicsCode("P002")).thenReturn(Optional.of(product));
        when(orderRepository.findByDynamicsOrderNumber("OP-002")).thenReturn(Optional.of(existing));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        stubBatchSave();

        // Act
        ImportProductionBatchResponse result = useCase.execute(file, "admin");

        // Assert
        assertThat(result.updatedRecords()).isEqualTo(1);
        assertThat(result.createdRecords()).isEqualTo(0);
        assertThat(existing.getStatus()).isEqualTo(ProductionOrderStatus.IN_PROGRESS);
        assertThat(existing.getProducedQty()).isEqualByComparingTo("80");
    }

    @Test
    void shouldPreserveHubManagedFields_onUpdate() throws Exception {
        // Arrange
        MockMultipartFile file = buildExcel("OP-003|P003|DONE|500|500|2024-04-01|2024-04-30");

        UUID productId = UUID.randomUUID();
        Product product = Product.builder().id(productId).dynamicsCode("P003")
                .name("Produto C").type(ProductType.FINISHED).active(true).build();
        ProductionOrder existing = ProductionOrder.builder()
                .id(UUID.randomUUID()).dynamicsOrderNumber("OP-003").product(product)
                .status(ProductionOrderStatus.IN_PROGRESS)
                .plannedQty(BigDecimal.valueOf(500)).producedQty(BigDecimal.ZERO)
                .plannedPeople(8)         // Hub-managed — must be preserved
                .peopleOverridden(true)   // Hub-managed — must be preserved
                .importedAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build();

        ArgumentCaptor<ProductionOrder> captor = ArgumentCaptor.forClass(ProductionOrder.class);
        when(productRepository.findByDynamicsCode("P003")).thenReturn(Optional.of(product));
        when(orderRepository.findByDynamicsOrderNumber("OP-003")).thenReturn(Optional.of(existing));
        when(orderRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));
        stubBatchSave();

        // Act
        ImportProductionBatchResponse result = useCase.execute(file, "admin");

        // Assert
        assertThat(result.updatedRecords()).isEqualTo(1);
        ProductionOrder saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(ProductionOrderStatus.DONE);  // updated
        assertThat(saved.getPlannedPeople()).isEqualTo(8);         // preserved
        assertThat(saved.isPeopleOverridden()).isTrue();            // preserved
    }

    // US-086 AC#2 — cálculo automático de staffing na importação

    @Test
    void shouldCalculateStaffingForNewOrders_whenCycleTimeExists() throws Exception {
        MockMultipartFile file = buildExcel("OP-010|P010|PLANNED|100|0||2030-12-31");

        Product product = Product.builder().id(UUID.randomUUID()).dynamicsCode("P010")
                .name("Produto X").type(ProductType.FINISHED).active(true).build();

        // 100 units * 288 sec/unit = 28800s; 1 shift * 8h * 3600 = 28800s/day; workdays ≈ large → 1 person
        CycleTime cycleTime = CycleTime.builder()
                .id(UUID.randomUUID()).product(product).secondsPerUnit(288.0)
                .effectiveDate(LocalDate.now()).build();
        StaffingConfig config = new StaffingConfig();
        config.setShiftHours(8);
        config.setShiftsPerDay(1);

        when(productRepository.findByDynamicsCode("P010")).thenReturn(Optional.of(product));
        when(orderRepository.findByDynamicsOrderNumber("OP-010")).thenReturn(Optional.empty());
        when(cycleTimeRepository.findTopByProductIdOrderByEffectiveDateDesc(product.getId()))
                .thenReturn(Optional.of(cycleTime));
        when(getStaffingConfig.getOrCreate()).thenReturn(config);

        ArgumentCaptor<ProductionOrder> captor = ArgumentCaptor.forClass(ProductionOrder.class);
        when(orderRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));
        stubBatchSave();

        useCase.execute(file, "admin");

        ProductionOrder saved = captor.getValue();
        assertThat(saved.getPlannedPeople()).isNotNull();
        assertThat(saved.getPlannedPeople()).isGreaterThanOrEqualTo(1);
        assertThat(saved.isPeopleOverridden()).isFalse();
    }

    @Test
    void shouldNotRecalculateStaffing_whenPeopleOverriddenIsTrue() throws Exception {
        MockMultipartFile file = buildExcel("OP-003|P003|DONE|500|500|2024-04-01|2024-04-30");

        UUID productId = UUID.randomUUID();
        Product product = Product.builder().id(productId).dynamicsCode("P003")
                .name("Produto C").type(ProductType.FINISHED).active(true).build();
        ProductionOrder existing = ProductionOrder.builder()
                .id(UUID.randomUUID()).dynamicsOrderNumber("OP-003").product(product)
                .status(ProductionOrderStatus.IN_PROGRESS)
                .plannedQty(BigDecimal.valueOf(500)).producedQty(BigDecimal.ZERO)
                .plannedPeople(8)
                .peopleOverridden(true)  // manually set — must not be recalculated
                .importedAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build();

        ArgumentCaptor<ProductionOrder> captor = ArgumentCaptor.forClass(ProductionOrder.class);
        when(productRepository.findByDynamicsCode("P003")).thenReturn(Optional.of(product));
        when(orderRepository.findByDynamicsOrderNumber("OP-003")).thenReturn(Optional.of(existing));
        when(orderRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));
        stubBatchSave();

        useCase.execute(file, "admin");

        ProductionOrder saved = captor.getValue();
        assertThat(saved.getPlannedPeople()).isEqualTo(8);   // preserved
        assertThat(saved.isPeopleOverridden()).isTrue();      // preserved
        verifyNoInteractions(cycleTimeRepository);            // no recalculation
    }

    /**
     * SEC-117 — StaffingConfig deve ser carregado UMA ÚNICA VEZ antes do loop,
     * independente do número de OPs processadas.
     */
    @Test
    void shouldLoadStaffingConfig_exactlyOnce_regardlessOfRowCount() throws Exception {
        // Given: planilha com 3 OPs (todas com peopleOverridden=false nas existentes)
        MockMultipartFile file = buildExcel(
                "OP-S1|P-SEC|PLANNED|10|0|2024-05-01|2024-05-15",
                "OP-S2|P-SEC|RELEASED|20|0|2024-05-01|2024-05-20",
                "OP-S3|P-SEC|IN_PROGRESS|30|10|2024-05-01|2024-05-25"
        );

        Product product = Product.builder().id(UUID.randomUUID()).dynamicsCode("P-SEC")
                .name("Produto SEC").type(ProductType.FINISHED).active(true).build();

        StaffingConfig config = new StaffingConfig();
        config.setShiftHours(8);
        config.setShiftsPerDay(1);

        when(productRepository.findByDynamicsCode("P-SEC")).thenReturn(Optional.of(product));
        when(orderRepository.findByDynamicsOrderNumber(anyString())).thenReturn(Optional.empty());
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(getStaffingConfig.getOrCreate()).thenReturn(config);
        when(cycleTimeRepository.findTopByProductIdOrderByEffectiveDateDesc(any()))
                .thenReturn(Optional.empty()); // sem CycleTime → plannedPeople = null
        stubBatchSave();

        // When
        useCase.execute(file, "admin");

        // Then: getOrCreate chamado exatamente 1 vez, independente das 3 OPs
        verify(getStaffingConfig, times(1)).getOrCreate();
    }

    @Test
    void shouldSkipRow_whenStatusIsInvalid() throws Exception {
        // Arrange
        MockMultipartFile file = buildExcel(
                "OP-100|P001|PLANNED|10|0|2024-05-01|2024-05-10",
                "OP-101|P001|INVALID_STATUS|10|0|2024-05-01|2024-05-10"
        );

        Product product = Product.builder().id(UUID.randomUUID()).dynamicsCode("P001")
                .name("Produto A").type(ProductType.FINISHED).active(true).build();
        when(productRepository.findByDynamicsCode("P001")).thenReturn(Optional.of(product));
        when(orderRepository.findByDynamicsOrderNumber("OP-100")).thenReturn(Optional.empty());
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        stubBatchSave();

        // Act
        ImportProductionBatchResponse result = useCase.execute(file, "admin");

        // Assert
        assertThat(result.createdRecords()).isEqualTo(1);
        assertThat(result.errorRecords()).isEqualTo(1);
        assertThat(result.errors().get(0).message()).contains("INVALID_STATUS");
    }
}
