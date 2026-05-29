package com.industrialhub.backend.production;

import com.industrialhub.backend.production.application.dto.CycleTimeResponse;
import com.industrialhub.backend.production.domain.CycleTime;
import com.industrialhub.backend.production.domain.Product;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * US-109 SEC-113 — CycleTimeResponse não deve expor importedBy (username de quem importou).
 * O campo de auditoria importedBy existe na entidade CycleTime mas não deve ser serializado
 * no DTO público (OPERATOR pode listar cycle times).
 */
class CycleTimeResponseTest {

    @Test
    void cycleTimeResponse_shouldNotExposeImportedByField() {
        // CycleTimeResponse não deve ter campo importedBy em seus record components
        var fieldNames = Arrays.stream(CycleTimeResponse.class.getRecordComponents())
                .map(rc -> rc.getName())
                .toList();

        assertThat(fieldNames).doesNotContain("importedBy");
    }

    @Test
    void cycleTimeResponse_fromEntity_shouldMapPublicFieldsOnly() {
        Product product = new Product();
        product.setId(UUID.randomUUID());
        product.setDynamicsCode("PROD-001");

        CycleTime entity = CycleTime.builder()
                .id(UUID.randomUUID())
                .product(product)
                .secondsPerUnit(12.5)
                .effectiveDate(LocalDate.of(2026, 1, 1))
                .importedBy("admin")          // campo de auditoria — não deve aparecer no DTO
                .importedAt(LocalDateTime.now())
                .build();

        CycleTimeResponse response = CycleTimeResponse.from(entity);

        // DTO contém apenas campos públicos (sem username)
        assertThat(response.id()).isNotNull();
        assertThat(response.productCode()).isEqualTo("PROD-001");
        assertThat(response.secondsPerUnit()).isEqualTo(12.5);
        assertThat(response.importedAt()).isNotNull();

        // Garantia estrutural: nenhum campo chamado importedBy
        assertThat(Arrays.stream(CycleTimeResponse.class.getRecordComponents())
                .map(rc -> rc.getName())
                .toList())
                .doesNotContain("importedBy");
    }
}
