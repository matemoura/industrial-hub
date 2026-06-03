package com.industrialhub.backend.qms;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.industrialhub.backend.qms.application.dto.ActionResponse;
import com.industrialhub.backend.qms.domain.ActionStatus;
import com.industrialhub.backend.qms.domain.ActionType;
import com.industrialhub.backend.qms.domain.CorrectiveAction;
import com.industrialhub.backend.qms.domain.NonConformance;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ActionResponseTest {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .findAndRegisterModules();

    @Test
    void newFields_type_and_effectivenessCheckDate_present_in_response() {
        UUID ncId = UUID.randomUUID();
        NonConformance nc = NonConformance.builder().id(ncId).title("NC").build();

        CorrectiveAction action = CorrectiveAction.builder()
                .id(UUID.randomUUID())
                .nonConformance(nc)
                .description("description")
                .responsible("user")
                .dueDate(LocalDate.now())
                .status(ActionStatus.PENDING)
                .type(ActionType.PREVENTIVE)
                .effectivenessCheckDate(LocalDate.of(2026, 7, 1))
                .build();

        ActionResponse response = ActionResponse.from(action);

        assertThat(response.type()).isEqualTo(ActionType.PREVENTIVE);
        assertThat(response.effectivenessCheckDate()).isEqualTo(LocalDate.of(2026, 7, 1));
    }

    @Test
    void actionStatus_PENDING_EFFECTIVENESS_serializedAsString() throws Exception {
        ActionStatus status = ActionStatus.PENDING_EFFECTIVENESS;
        String json = objectMapper.writeValueAsString(status);
        assertThat(json).isEqualTo("\"PENDING_EFFECTIVENESS\"");
    }
}
