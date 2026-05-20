package com.industrialhub.backend.maintenance;

import com.industrialhub.backend.common.application.AuditService;
import com.industrialhub.backend.maintenance.application.dto.CreateSparePartRequest;
import com.industrialhub.backend.maintenance.application.dto.SparePartResponse;
import com.industrialhub.backend.maintenance.application.usecase.CreateSparePartUseCase;
import com.industrialhub.backend.maintenance.domain.SparePart;
import com.industrialhub.backend.maintenance.domain.SparePartDuplicateCodeException;
import com.industrialhub.backend.maintenance.infrastructure.SparePartRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CreateSparePartUseCaseTest {

    @Mock
    private SparePartRepository sparePartRepository;

    @Mock
    private AuditService auditService;

    private CreateSparePartUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new CreateSparePartUseCase(sparePartRepository, auditService);
    }

    @Test
    void shouldCreateSparePartSuccessfully() {
        SparePart saved = SparePart.builder()
                .id(UUID.randomUUID())
                .code("PART-001")
                .name("Rolamento SKF")
                .category("Mecânico")
                .unit("un")
                .stockQty(10)
                .minStockQty(2)
                .active(true)
                .build();
        when(sparePartRepository.save(any())).thenReturn(saved);
        doNothing().when(sparePartRepository).flush();

        CreateSparePartRequest request = new CreateSparePartRequest(
                "PART-001", "Rolamento SKF", "Mecânico", "un", 10, 2);

        SparePartResponse response = useCase.execute(request, "admin");

        assertThat(response.code()).isEqualTo("PART-001");
        assertThat(response.stockQty()).isEqualTo(10);
        assertThat(response.belowMin()).isFalse();
        verify(sparePartRepository).save(any());
        verify(auditService).log(eq("admin"), any(), eq("SparePart"), any(String.class), any());
    }

    @Test
    void shouldThrowOnDuplicateCode() {
        when(sparePartRepository.save(any())).thenThrow(new DataIntegrityViolationException("dup"));

        CreateSparePartRequest request = new CreateSparePartRequest(
                "PART-001", "Rolamento SKF", "Mecânico", "un", 10, 2);

        assertThatThrownBy(() -> useCase.execute(request, "admin"))
                .isInstanceOf(SparePartDuplicateCodeException.class);
    }

    @Test
    void belowMinFlagShouldBeTrueWhenStockBelowMinimum() {
        SparePart saved = SparePart.builder()
                .id(UUID.randomUUID())
                .code("PART-002")
                .name("Filtro")
                .category("Filtros")
                .unit("un")
                .stockQty(1)
                .minStockQty(5)
                .active(true)
                .build();
        when(sparePartRepository.save(any())).thenReturn(saved);
        doNothing().when(sparePartRepository).flush();

        CreateSparePartRequest request = new CreateSparePartRequest(
                "PART-002", "Filtro", "Filtros", "un", 1, 5);

        SparePartResponse response = useCase.execute(request, "admin");

        assertThat(response.belowMin()).isTrue();
    }
}
