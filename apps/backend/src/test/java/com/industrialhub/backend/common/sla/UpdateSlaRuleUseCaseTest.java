package com.industrialhub.backend.common.sla;

import com.industrialhub.backend.common.application.AuditService;
import com.industrialhub.backend.common.application.dto.SlaRuleResponse;
import com.industrialhub.backend.common.application.dto.UpdateSlaRuleRequest;
import com.industrialhub.backend.common.application.usecase.UpdateSlaRuleUseCase;
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
class UpdateSlaRuleUseCaseTest {

    @Mock SlaRuleRepository slaRuleRepository;
    @Mock AuditService auditService;
    @InjectMocks UpdateSlaRuleUseCase useCase;

    @Test
    void update_success() {
        UUID id = UUID.randomUUID();
        SlaRule existing = SlaRule.builder()
            .id(id)
            .entityType(SlaEntityType.NC)
            .classifierField(SlaClassifierField.SEVERITY)
            .classifierValue("CRITICAL")
            .slaHours(48)
            .escalateByEmail(false)
            .active(true)
            .createdAt(LocalDateTime.now())
            .build();

        when(slaRuleRepository.findById(id)).thenReturn(Optional.of(existing));
        when(slaRuleRepository.save(any())).thenReturn(existing);

        UpdateSlaRuleRequest request = new UpdateSlaRuleRequest(72, true);
        SlaRuleResponse response = useCase.execute(id, request, "admin");

        assertThat(response.slaHours()).isEqualTo(72);
        assertThat(response.escalateByEmail()).isTrue();
        verify(auditService).log(any(), any(), any(), any(String.class), any());
    }

    @Test
    void update_notFound_throws() {
        UUID id = UUID.randomUUID();
        when(slaRuleRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(id, new UpdateSlaRuleRequest(24, false), "admin"))
            .isInstanceOf(SlaRuleNotFoundException.class);
    }

    @Test
    void update_inactiveRule_throws() {
        UUID id = UUID.randomUUID();
        SlaRule inactive = SlaRule.builder()
            .id(id).entityType(SlaEntityType.NC).classifierField(SlaClassifierField.SEVERITY)
            .classifierValue("HIGH").slaHours(72).escalateByEmail(false).active(false)
            .createdAt(LocalDateTime.now()).build();

        when(slaRuleRepository.findById(id)).thenReturn(Optional.of(inactive));

        assertThatThrownBy(() -> useCase.execute(id, new UpdateSlaRuleRequest(24, false), "admin"))
            .isInstanceOf(SlaRuleNotFoundException.class);
    }
}
