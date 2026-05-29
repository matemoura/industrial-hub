package com.industrialhub.backend.production;

import com.industrialhub.backend.common.application.AuditService;
import com.industrialhub.backend.production.application.dto.BomImportResponse;
import com.industrialhub.backend.production.application.usecase.ImportBomUseCase;
import com.industrialhub.backend.production.domain.*;
import com.industrialhub.backend.production.infrastructure.ProductComponentRepository;
import com.industrialhub.backend.production.infrastructure.ProductRepository;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ImportBomUseCaseTest {

    @Mock ProductRepository productRepository;
    @Mock ProductComponentRepository componentRepository;
    @Mock AuditService auditService;
    @InjectMocks ImportBomUseCase useCase;

    private MockMultipartFile buildExcel(String[][] data) throws Exception {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("BOM");
            // Header
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("parent_code");
            header.createCell(1).setCellValue("component_code");
            header.createCell(2).setCellValue("quantity");
            header.createCell(3).setCellValue("unit");
            // Data rows
            for (int i = 0; i < data.length; i++) {
                Row row = sheet.createRow(i + 1);
                row.createCell(0).setCellValue(data[i][0]);
                row.createCell(1).setCellValue(data[i][1]);
                row.createCell(2).setCellValue(Double.parseDouble(data[i][2]));
                row.createCell(3).setCellValue(data[i][3]);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return new MockMultipartFile("file", "bom.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    out.toByteArray());
        }
    }

    @Test
    void shouldImportBom_createNewComponents() throws Exception {
        // Given: planilha com 2 componentes para PROD-001
        MockMultipartFile file = buildExcel(new String[][]{
                {"PROD-001", "COMP-101", "2.0", "UN"},
                {"PROD-001", "RAW-201",  "0.5", "KG"}
        });

        Product parent = new Product();
        parent.setId(UUID.randomUUID());
        parent.setDynamicsCode("PROD-001");
        parent.setType(ProductType.FINISHED);

        Product comp1 = new Product();
        comp1.setId(UUID.randomUUID());
        comp1.setDynamicsCode("COMP-101");
        comp1.setType(ProductType.INTERMEDIATE);

        Product comp2 = new Product();
        comp2.setId(UUID.randomUUID());
        comp2.setDynamicsCode("RAW-201");
        comp2.setType(ProductType.RAW_MATERIAL);

        when(productRepository.findByDynamicsCode("PROD-001")).thenReturn(Optional.of(parent));
        when(productRepository.findByDynamicsCode("COMP-101")).thenReturn(Optional.of(comp1));
        when(productRepository.findByDynamicsCode("RAW-201")).thenReturn(Optional.of(comp2));
        when(componentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // When
        BomImportResponse response = useCase.execute(file, "admin1");

        // Then
        assertThat(response.totalRecords()).isEqualTo(2);
        assertThat(response.created()).isEqualTo(2);
        assertThat(response.errors()).isEqualTo(0);
        // ADR-044 Decisão 2: deleteByParentProductCode chamado antes de inserir
        verify(componentRepository).deleteByParentProductCode("PROD-001");
        verify(componentRepository, times(2)).save(any(ProductComponent.class));
    }

    @Test
    void shouldSkipRow_whenComponentProductNotFound() throws Exception {
        // Given: linha com componente inexistente
        MockMultipartFile file = buildExcel(new String[][]{
                {"PROD-001", "COMP-UNKNOWN", "1.0", "UN"}
        });

        Product parent = new Product();
        parent.setId(UUID.randomUUID());
        parent.setDynamicsCode("PROD-001");
        parent.setType(ProductType.FINISHED);

        when(productRepository.findByDynamicsCode("PROD-001")).thenReturn(Optional.of(parent));
        when(productRepository.findByDynamicsCode("COMP-UNKNOWN")).thenReturn(Optional.empty());

        // When
        BomImportResponse response = useCase.execute(file, "admin1");

        // Then
        assertThat(response.totalRecords()).isEqualTo(1);
        assertThat(response.created()).isEqualTo(0);
        assertThat(response.errors()).isEqualTo(1);
        assertThat(response.errorDetails().get(0).message()).contains("COMP-UNKNOWN");
        // Delete ainda executado (parent existe)
        verify(componentRepository).deleteByParentProductCode("PROD-001");
        verify(componentRepository, never()).save(any());
    }

    @Test
    void shouldSkipAllRows_whenParentProductNotFound() throws Exception {
        // Given: parent_code não existe no catálogo
        MockMultipartFile file = buildExcel(new String[][]{
                {"PROD-GHOST", "COMP-101", "1.0", "UN"}
        });

        when(productRepository.findByDynamicsCode("PROD-GHOST")).thenReturn(Optional.empty());

        // When
        BomImportResponse response = useCase.execute(file, "admin1");

        // Then
        assertThat(response.totalRecords()).isEqualTo(1);
        assertThat(response.created()).isEqualTo(0);
        assertThat(response.errors()).isEqualTo(1);
        assertThat(response.errorDetails().get(0).message()).contains("PROD-GHOST");
        // ADR-044 Decisão 2: delete NÃO executado quando parent não existe (evitar delete de BOM de produto errado)
        verify(componentRepository, never()).deleteByParentProductCode(any());
        verify(componentRepository, never()).save(any());
    }
}
