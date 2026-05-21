package com.industrialhub.backend.common.sla;

import com.industrialhub.backend.common.application.AuditService;
import com.industrialhub.backend.common.application.usecase.DeleteSlaRuleUseCase;
import com.industrialhub.backend.common.domain.SlaClassifierField;
import com.industrialhub.backend.common.domain.SlaEntityType;
import com.industrialhub.backend.common.domain.SlaRule;
import com.industrialhub.backend.common.domain.SlaRuleNotFoundException;
import com.industrialhub.backend.common.infrastructure.SlaRuleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeleteSlaRuleUseCaseTest {

    @Mock SlaRuleRepository slaRuleRepository;
    @Mock AuditService auditService;
    @InjectMocks DeleteSlaRuleUseCase useCase;

    @Test
    void delete_success_softDeletes() {
        UUID id = UUID.randomUUID();
        SlaRule rule = SlaRule.builder()
            .id(id).entityType(SlaEntityType.NC).classifierField(SlaClassifierField.SEVERITY)
            .classifierValue("HIGH").slaHours(72).escalateByEmail(false).active(true)
            .createdAt(LocalDateTime.now()).build();

        when(slaRuleRepository.findById(id)).thenReturn(Optional.of(rule));
        when(slaRuleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        useCase.execute(id, "admin");

        assertThat(rule.isActive()).isFalse();
        verify(slaRuleRepository).save(rule);
        verify(auditService).log(any(), any(), any(), any(String.class), any());
    }

    @Test
    void delete_notFound_throws() {
        UUID id = UUID.randomUUID();
        when(slaRuleRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(id, "admin"))
            .isInstanceOf(SlaRuleNotFoundException.class);

        verify(slaRuleRepository, never()).save(any());
    }
}
