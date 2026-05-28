package com.industrialhub.backend.production;

import com.industrialhub.backend.common.application.AuditService;
import com.industrialhub.backend.production.application.dto.MrpPlannedOrderResponse;
import com.industrialhub.backend.production.application.usecase.AcceptMrpSuggestionUseCase;
import com.industrialhub.backend.production.domain.*;
import com.industrialhub.backend.production.infrastructure.MrpPlannedOrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AcceptMrpSuggestionUseCaseTest {

    @Mock MrpPlannedOrderRepository mrpPlannedOrderRepository;
    @Mock AuditService auditService;
    @InjectMocks AcceptMrpSuggestionUseCase useCase;

    private MrpPlannedOrder makeSuggestion(MrpOrderStatus status) {
        Product product = new Product();
        product.setId(UUID.randomUUID());
        product.setDynamicsCode("P001");
        product.setName("Produto A");
        product.setType(ProductType.FINISHED);

        MrpPlannedOrder s = new MrpPlannedOrder();
        s.setId(UUID.randomUUID());
        s.setProduct(product);
        s.setSuggestedQty(100);
        s.setSuggestedDueDate(LocalDate.now().plusDays(7));
        s.setStatus(status);
        return s;
    }

    @Test
    void shouldAcceptSuggestionWithoutAdjustment() {
        MrpPlannedOrder suggestion = makeSuggestion(MrpOrderStatus.SUGGESTED);
        when(mrpPlannedOrderRepository.findById(suggestion.getId())).thenReturn(Optional.of(suggestion));
        when(mrpPlannedOrderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        MrpPlannedOrderResponse result = useCase.execute(suggestion.getId(), null, "supervisor1");

        assertThat(result.status()).isEqualTo(MrpOrderStatus.ACCEPTED);
        assertThat(result.adjustedQty()).isNull();
        assertThat(result.reviewedBy()).isEqualTo("supervisor1");
    }

    @Test
    void shouldPersistAdjustedQtyWhenProvided() {
        MrpPlannedOrder suggestion = makeSuggestion(MrpOrderStatus.SUGGESTED);
        when(mrpPlannedOrderRepository.findById(suggestion.getId())).thenReturn(Optional.of(suggestion));
        when(mrpPlannedOrderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        useCase.execute(suggestion.getId(), 80, "supervisor1");

        assertThat(suggestion.getAdjustedQty()).isEqualTo(80);
    }

    @Test
    void shouldThrow409WhenSuggestionNotSuggested() {
        MrpPlannedOrder suggestion = makeSuggestion(MrpOrderStatus.REJECTED);
        when(mrpPlannedOrderRepository.findById(suggestion.getId())).thenReturn(Optional.of(suggestion));

        assertThatThrownBy(() -> useCase.execute(suggestion.getId(), null, "supervisor1"))
                .isInstanceOf(InvalidMrpSuggestionStatusException.class)
                .hasMessageContaining("REJECTED");
    }

    @Test
    void shouldThrow404WhenSuggestionNotFound() {
        UUID id = UUID.randomUUID();
        when(mrpPlannedOrderRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(id, null, "supervisor1"))
                .isInstanceOf(MrpSuggestionNotFoundException.class);
    }
}
