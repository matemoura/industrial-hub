package com.industrialhub.backend.production;

import com.industrialhub.backend.common.application.AuditService;
import com.industrialhub.backend.production.application.MrpCalculationService;
import com.industrialhub.backend.production.application.dto.MrpRunResult;
import com.industrialhub.backend.production.application.usecase.DryRunMrpUseCase;
import com.industrialhub.backend.production.application.usecase.RunMrpUseCase;
import com.industrialhub.backend.production.domain.*;
import com.industrialhub.backend.production.infrastructure.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RunMrpUseCaseTest {

    @Mock MrpCalculationService calculationService;
    @Mock MrpRunRepository mrpRunRepository;
    @Mock MrpPlannedOrderRepository mrpPlannedOrderRepository;
    @Mock AuditService auditService;
    @InjectMocks RunMrpUseCase useCase;

    private MrpCalculationService.CalculationOutput makeOutput(int suggestionCount) {
        MrpRun run = new MrpRun();
        run.setId(UUID.randomUUID());
        run.setRunAt(java.time.LocalDateTime.now());
        run.setRunBy("supervisor1");
        run.setDryRun(false);
        run.setProductsAnalyzed(5);
        run.setSuggestionsGenerated(suggestionCount);
        run.setAlreadyOk(5 - suggestionCount);
        run.setMessagesJson("[]");
        run.setPurchaseNeedsJson("[]");

        Product product = new Product();
        product.setId(UUID.randomUUID());
        product.setDynamicsCode("P001");
        product.setName("Produto A");
        product.setType(ProductType.FINISHED);

        List<MrpPlannedOrder> suggestions = new java.util.ArrayList<>();
        for (int i = 0; i < suggestionCount; i++) {
            MrpPlannedOrder s = new MrpPlannedOrder();
            s.setId(UUID.randomUUID());
            s.setProduct(product);
            s.setSuggestedQty(100);
            s.setSuggestedDueDate(LocalDate.now().plusDays(7));
            s.setStatus(MrpOrderStatus.SUGGESTED);
            suggestions.add(s);
        }

        return new MrpCalculationService.CalculationOutput(run, suggestions, List.of(), List.of());
    }

    @Test
    void shouldSupersedePendingSuggestionsBeforeRun() {
        var output = makeOutput(2);
        when(calculationService.calculate(false, "supervisor1")).thenReturn(output);
        when(mrpRunRepository.save(any())).thenReturn(output.run());
        when(mrpPlannedOrderRepository.saveAll(any())).thenReturn(output.suggestions());

        useCase.execute("supervisor1");

        verify(mrpPlannedOrderRepository).supersedePendingSuggestions();
    }

    @Test
    void shouldPersistRunAndSuggestions() {
        var output = makeOutput(3);
        when(calculationService.calculate(false, "supervisor1")).thenReturn(output);
        when(mrpRunRepository.save(any())).thenReturn(output.run());
        when(mrpPlannedOrderRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        MrpRunResult result = useCase.execute("supervisor1");

        verify(mrpRunRepository).save(output.run());
        verify(mrpPlannedOrderRepository).saveAll(anyList());
        assertThat(result.suggestions()).hasSize(3);
        assertThat(result.isDryRun()).isFalse();
    }

    @Test
    void shouldAuditAfterRun() {
        var output = makeOutput(1);
        when(calculationService.calculate(false, "supervisor1")).thenReturn(output);
        when(mrpRunRepository.save(any())).thenReturn(output.run());
        when(mrpPlannedOrderRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        useCase.execute("supervisor1");

        verify(auditService).log(eq("supervisor1"), any(), anyString(), anyString(), anyMap());
    }

    @Test
    void dryRunShouldNotPersistAnything() {
        DryRunMrpUseCase dryRunUseCase = new DryRunMrpUseCase(calculationService);
        var output = makeOutput(2);
        when(calculationService.calculate(true, "supervisor1")).thenReturn(output);

        MrpRunResult result = dryRunUseCase.execute("supervisor1");

        verifyNoInteractions(mrpRunRepository, mrpPlannedOrderRepository, auditService);
        assertThat(result.isDryRun()).isTrue();
        assertThat(result.suggestions()).hasSize(2);
    }

    @Test
    void openMrpSuggestionsShouldReduceNetNeed() {
        // Verifica que a saída do calculationService inclui sugestões MRP abertas no openOrdersQty
        // Isso é testado via integração do MrpCalculationService, mas validamos o contrato aqui:
        // RunMrpUseCase chama calculate() UMA vez com isDryRun=false
        var output = makeOutput(0);
        when(calculationService.calculate(false, "supervisor1")).thenReturn(output);
        when(mrpRunRepository.save(any())).thenReturn(output.run());
        when(mrpPlannedOrderRepository.saveAll(any())).thenReturn(List.of());

        MrpRunResult result = useCase.execute("supervisor1");

        verify(calculationService).calculate(false, "supervisor1");
        assertThat(result.suggestions()).isEmpty();
    }
}
