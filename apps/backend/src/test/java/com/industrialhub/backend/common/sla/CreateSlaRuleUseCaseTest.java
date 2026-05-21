package com.industrialhub.backend.common.sla;

import com.industrialhub.backend.common.application.AuditService;
import com.industrialhub.backend.common.application.dto.CreateSlaRuleRequest;
import com.industrialhub.backend.common.application.dto.SlaRuleResponse;
import com.industrialhub.backend.common.application.usecase.CreateSlaRuleUseCase;
import com.industrialhub.backend.common.domain.SlaClassifierField;
import com.industrialhub.backend.common.domain.SlaEntityType;
import com.industrialhub.backend.common.domain.SlaRule;
import com.industrialhub.backend.common.domain.SlaRuleDuplicateException;
import com.industrialhub.backend.common.infrastructure.SlaRuleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CreateSlaRuleUseCaseTest {

    @Mock SlaRuleRepository slaRuleRepository;
    @Mock AuditService auditService;
    @InjectMocks CreateSlaRuleUseCase useCase;

    @Test
    void create_success() {
        CreateSlaRuleRequest request = new CreateSlaRuleRequest(
            SlaEntityType.NC, SlaClassifierField.SEVERITY, "CRITICAL", 48, true);

        when(slaRuleRepository.existsByEntityTypeAndClassifierFieldAndClassifierValueAndActiveTrue(
            any(), any(), any())).thenReturn(false);

        SlaRule saved = SlaRule.builder()
            .id(UUID.randomUUID())
            .entityType(SlaEntityType.NC)
            .classifierField(SlaClassifierField.SEVERITY)
            .classifierValue("CRITICAL")
            .slaHours(48)
            .escalateByEmail(true)
            .active(true)
            .createdAt(LocalDateTime.now())
            .build();
        when(slaRuleRepository.save(any())).thenReturn(saved);

        SlaRuleResponse response = useCase.execute(request, "admin");

        assertThat(response.entityType()).isEqualTo(SlaEntityType.NC);
        assertThat(response.classifierValue()).isEqualTo("CRITICAL");
        assertThat(response.slaHours()).isEqualTo(48);
        assertThat(response.escalateByEmail()).isTrue();
        verify(auditService).log(eq("admin"), any(), eq("SlaRule"), any(String.class), any());
    }

    @Test
    void create_duplicate_throwsSlaRuleDuplicateException() {
        CreateSlaRuleRequest request = new CreateSlaRuleRequest(
            SlaEntityType.NC, SlaClassifierField.SEVERITY, "CRITICAL", 48, true);

        when(slaRuleRepository.existsByEntityTypeAndClassifierFieldAndClassifierValueAndActiveTrue(
            any(), any(), any())).thenReturn(true);

        assertThatThrownBy(() -> useCase.execute(request, "admin"))
            .isInstanceOf(SlaRuleDuplicateException.class)
            .hasMessageContaining("já existe");

        verify(slaRuleRepository, never()).save(any());
    }
}
