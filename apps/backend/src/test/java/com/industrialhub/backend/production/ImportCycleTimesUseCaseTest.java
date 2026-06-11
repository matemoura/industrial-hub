package com.industrialhub.backend.production;

import com.industrialhub.backend.common.application.AuditService;
import com.industrialhub.backend.production.application.dto.ImportProductionBatchResponse;
import com.industrialhub.backend.production.application.usecase.ImportCycleTimesUseCase;
import com.industrialhub.backend.production.domain.*;
import com.industrialhub.backend.production.infrastructure.CycleTimeRepository;
import com.industrialhub.backend.production.infrastructure.ImportProductionBatchRepository;
import com.industrialhub.backend.production.infrastructure.ProductRepository;
import com.industrialhub.backend.production.infrastructure.ProductionOrderRepository;
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
class ImportCycleTimesUseCaseTest {

    @Mock private ProductRepository productRepository;
    @Mock private CycleTimeRepository cycleTimeRepository;
    @Mock private ImportProductionBatchRepository batchRepository;
    @Mock private ProductionOrderRepository orderRepository;
    @Mock private AuditService auditService;

    private ImportCycleTimesUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ImportCycleTimesUseCase(
                productRepository, cycleTimeRepository, batchRepository, orderRepository, auditService);
    }

    /**
     * Builds an Excel file with Dynamics-format columns:
     * número | tipo | quantidade/tempo | data_física
     * Each row string uses '|' as separator.
     */
    private MockMultipartFile buildExcel(String... rows) throws Exception {
        Workbook wb = new XSSFWorkbook();
        Sheet sheet = wb.createSheet();
        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("número");
        header.createCell(1).setCellValue("tipo");
        header.createCell(2).setCellValue("quantidade/tempo");
        header.createCell(3).setCellValue("data_física");

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

    private ProductionOrder orderFor(Product product, String opNumber) {
        return ProductionOrder.builder()
                .id(UUID.randomUUID())
                .dynamicsOrderNumber(opNumber)
                .product(product)
                .status(ProductionOrderStatus.DONE)
                .plannedQty(BigDecimal.valueOf(100))
                .dueDate(LocalDate.of(2024, 6, 30))
                .importedAt(LocalDateTime.now())
                .build();
    }

    @Test
    void shouldCreateCycleTime_fromHoraAndQuantidadeRows() throws Exception {
        // 1h produzindo 10 unidades → 360 s/unidade
        MockMultipartFile file = buildExcel(
                "OP001|Hora|1.0|2024-06-01",
                "OP001|Quantidade|10.0|2024-06-01"
        );

        UUID productId = UUID.randomUUID();
        Product product = Product.builder().id(productId).dynamicsCode("P001")
                .name("Produto A").type(ProductType.FINISHED).active(true).build();

        when(orderRepository.findByDynamicsOrderNumber("OP001"))
                .thenReturn(Optional.of(orderFor(product, "OP001")));
        when(cycleTimeRepository.findByProductIdAndEffectiveDate(productId, LocalDate.of(2024, 6, 1)))
                .thenReturn(Optional.empty());
        when(cycleTimeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        stubBatchSave();

        ImportProductionBatchResponse result = useCase.execute(file, "admin");

        assertThat(result.createdRecords()).isEqualTo(1);
        assertThat(result.updatedRecords()).isEqualTo(0);

        ArgumentCaptor<CycleTime> captor = ArgumentCaptor.forClass(CycleTime.class);
        verify(cycleTimeRepository).save(captor.capture());
        assertThat(captor.getValue().getSecondsPerUnit()).isEqualTo(360.0);
        assertThat(captor.getValue().getEffectiveDate()).isEqualTo(LocalDate.of(2024, 6, 1));
    }

    @Test
    void shouldUpdateExistingCycleTime_whenSameProductAndMonth() throws Exception {
        // Novo apontamento no mesmo mês → atualiza o registro existente
        MockMultipartFile file = buildExcel(
                "OP002|Hora|2.0|2024-06-15",
                "OP002|Quantidade|20.0|2024-06-15"
        );

        UUID productId = UUID.randomUUID();
        Product product = Product.builder().id(productId).dynamicsCode("P002")
                .name("Produto B").type(ProductType.FINISHED).active(true).build();
        CycleTime existing = CycleTime.builder().id(UUID.randomUUID()).product(product)
                .secondsPerUnit(400.0).effectiveDate(LocalDate.of(2024, 6, 1))
                .importedBy("old-user").importedAt(LocalDateTime.now()).build();

        when(orderRepository.findByDynamicsOrderNumber("OP002"))
                .thenReturn(Optional.of(orderFor(product, "OP002")));
        when(cycleTimeRepository.findByProductIdAndEffectiveDate(productId, LocalDate.of(2024, 6, 1)))
                .thenReturn(Optional.of(existing));
        when(cycleTimeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        stubBatchSave();

        ImportProductionBatchResponse result = useCase.execute(file, "admin");

        assertThat(result.updatedRecords()).isEqualTo(1);
        assertThat(result.createdRecords()).isEqualTo(0);
        assertThat(existing.getSecondsPerUnit()).isEqualTo(360.0); // (2h / 20 unid) × 3600
    }

    @Test
    void shouldAggregateMultipleOps_forSameProductAndMonth() throws Exception {
        // Duas OPs do mesmo produto no mesmo mês → acumula horas e quantidade
        // OP003: 1h / 10 unid; OP004: 2h / 20 unid → total 3h / 30 unid = 360 s/unid
        MockMultipartFile file = buildExcel(
                "OP003|Hora|1.0|2024-07-05",
                "OP003|Quantidade|10.0|2024-07-05",
                "OP004|Hora|2.0|2024-07-20",
                "OP004|Quantidade|20.0|2024-07-20"
        );

        UUID productId = UUID.randomUUID();
        Product product = Product.builder().id(productId).dynamicsCode("P003")
                .name("Produto C").type(ProductType.FINISHED).active(true).build();

        when(orderRepository.findByDynamicsOrderNumber("OP003"))
                .thenReturn(Optional.of(orderFor(product, "OP003")));
        when(orderRepository.findByDynamicsOrderNumber("OP004"))
                .thenReturn(Optional.of(orderFor(product, "OP004")));
        when(cycleTimeRepository.findByProductIdAndEffectiveDate(productId, LocalDate.of(2024, 7, 1)))
                .thenReturn(Optional.empty());
        when(cycleTimeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        stubBatchSave();

        ImportProductionBatchResponse result = useCase.execute(file, "admin");

        assertThat(result.createdRecords()).isEqualTo(1); // um registro por (produto, mês)
        ArgumentCaptor<CycleTime> captor = ArgumentCaptor.forClass(CycleTime.class);
        verify(cycleTimeRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getSecondsPerUnit()).isEqualTo(360.0);
    }

    @Test
    void shouldCreateSeparateRecords_forDistinctMonths() throws Exception {
        // Mesma OP aparece em meses diferentes → dois registros de cycle time
        MockMultipartFile file = buildExcel(
                "OP005|Hora|1.0|2024-01-10",
                "OP005|Quantidade|10.0|2024-01-10",
                "OP006|Hora|1.0|2024-07-10",
                "OP006|Quantidade|10.0|2024-07-10"
        );

        UUID productId = UUID.randomUUID();
        Product product = Product.builder().id(productId).dynamicsCode("P005")
                .name("Produto E").type(ProductType.FINISHED).active(true).build();

        when(orderRepository.findByDynamicsOrderNumber("OP005"))
                .thenReturn(Optional.of(orderFor(product, "OP005")));
        when(orderRepository.findByDynamicsOrderNumber("OP006"))
                .thenReturn(Optional.of(orderFor(product, "OP006")));
        when(cycleTimeRepository.findByProductIdAndEffectiveDate(eq(productId), any()))
                .thenReturn(Optional.empty());
        when(cycleTimeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        stubBatchSave();

        ImportProductionBatchResponse result = useCase.execute(file, "admin");

        assertThat(result.createdRecords()).isEqualTo(2);
        verify(cycleTimeRepository, times(2)).save(any(CycleTime.class));
    }

    @Test
    void shouldSkipAndAddError_whenOrderNotFound() throws Exception {
        MockMultipartFile file = buildExcel(
                "OP_GHOST|Hora|1.0|2024-06-01",
                "OP_GHOST|Quantidade|10.0|2024-06-01"
        );

        when(orderRepository.findByDynamicsOrderNumber("OP_GHOST")).thenReturn(Optional.empty());
        stubBatchSave();

        ImportProductionBatchResponse result = useCase.execute(file, "admin");

        assertThat(result.errorRecords()).isEqualTo(1);
        assertThat(result.createdRecords()).isEqualTo(0);
        assertThat(result.errors()).hasSize(1);
        assertThat(result.errors().get(0).message()).contains("OP_GHOST");
        verify(cycleTimeRepository, never()).save(any());
    }
}
