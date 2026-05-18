package com.industrialhub.backend.qms;

import com.industrialhub.backend.qms.application.dto.SupplierQualityScore;
import com.industrialhub.backend.qms.application.usecase.GetSupplierQualityUseCase;
import com.industrialhub.backend.qms.domain.NcSeverity;
import com.industrialhub.backend.qms.domain.NcStatus;
import com.industrialhub.backend.qms.domain.NcType;
import com.industrialhub.backend.qms.domain.NonConformance;
import com.industrialhub.backend.qms.domain.Supplier;
import com.industrialhub.backend.qms.domain.SupplierNotFoundException;
import com.industrialhub.backend.qms.infrastructure.NonConformanceRepository;
import com.industrialhub.backend.qms.infrastructure.SupplierRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetSupplierQualityUseCaseTest {

    @Mock
    private SupplierRepository supplierRepository;

    @Mock
    private NonConformanceRepository ncRepository;

    private GetSupplierQualityUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GetSupplierQualityUseCase(supplierRepository, ncRepository);
    }

    @Test
    void shouldComputeScoreCorrectly_withMixedSeverities() {
        UUID supplierId = UUID.randomUUID();
        Supplier supplier = Supplier.builder().id(supplierId).name("Acme Ltda").active(true).build();

        List<NonConformance> ncs = List.of(
            buildNc(NcSeverity.CRITICAL, supplier),
            buildNc(NcSeverity.HIGH, supplier),
            buildNc(NcSeverity.MEDIUM, supplier),
            buildNc(NcSeverity.LOW, supplier)
        );

        when(supplierRepository.findById(supplierId)).thenReturn(Optional.of(supplier));
        when(ncRepository.findBySupplierIdAndReportedAtAfter(eq(supplierId), any())).thenReturn(ncs);

        SupplierQualityScore score = useCase.executeForSupplier(supplierId, 90);

        assertThat(score.totalNcs()).isEqualTo(4);
        assertThat(score.criticalNcs()).isEqualTo(1);
        assertThat(score.highNcs()).isEqualTo(1);
        // penalty = (1*5 + 1*2 + 1*1 + 1*0.5) / 4 * 100 = 8.5 / 4 * 100 = 212.5 → capped at 100
        // score = max(0, 100 - 212.5) → 0... wait, let me recalculate
        // penalty = (5 + 2 + 1 + 0.5) / max(4,1) * 100 = 8.5 / 4 * 100 = 212.5
        // score = max(0, 100 - 212.5) = 0
        assertThat(score.qualityScore()).isEqualTo(0.0);
    }

    @Test
    void shouldReturnPerfectScore_whenNoNcs() {
        UUID supplierId = UUID.randomUUID();
        Supplier supplier = Supplier.builder().id(supplierId).name("Perfect Co").active(true).build();

        when(supplierRepository.findById(supplierId)).thenReturn(Optional.of(supplier));
        when(ncRepository.findBySupplierIdAndReportedAtAfter(eq(supplierId), any())).thenReturn(List.of());

        SupplierQualityScore score = useCase.executeForSupplier(supplierId, 90);

        // penalty = 0 / max(0,1) * 100 = 0; score = 100
        assertThat(score.totalNcs()).isEqualTo(0);
        assertThat(score.qualityScore()).isEqualTo(100.0);
    }

    @Test
    void shouldThrowSupplierNotFoundException_whenSupplierDoesNotExist() {
        UUID supplierId = UUID.randomUUID();
        when(supplierRepository.findById(supplierId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.executeForSupplier(supplierId, 90))
                .isInstanceOf(SupplierNotFoundException.class);
    }

    @Test
    void shouldReturnRankingSortedByScoreAscending() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        Supplier s1 = Supplier.builder().id(id1).name("Bad Supplier").active(true).build();
        Supplier s2 = Supplier.builder().id(id2).name("Good Supplier").active(true).build();

        NonConformance criticalNc = buildNc(NcSeverity.CRITICAL, s1);

        when(supplierRepository.findAllByActiveTrue()).thenReturn(List.of(s1, s2));
        when(ncRepository.findByTypeAndReportedAtAfter(eq(NcType.SUPPLIER), any()))
                .thenReturn(List.of(criticalNc));

        List<SupplierQualityScore> ranking = useCase.executeRanking(90);

        assertThat(ranking).hasSize(2);
        // s1 tem NC crítica → score baixo (0.0); s2 não tem NCs → score 100.0
        assertThat(ranking.get(0).supplierId()).isEqualTo(id1);
        assertThat(ranking.get(1).supplierId()).isEqualTo(id2);
        assertThat(ranking.get(0).qualityScore()).isLessThan(ranking.get(1).qualityScore());
    }

    private NonConformance buildNc(NcSeverity severity, Supplier supplier) {
        return NonConformance.builder()
                .id(UUID.randomUUID())
                .title("NC teste")
                .type(NcType.SUPPLIER)
                .severity(severity)
                .status(NcStatus.OPEN)
                .reportedBy("op1")
                .reportedAt(LocalDateTime.now())
                .supplier(supplier)
                .build();
    }
}
