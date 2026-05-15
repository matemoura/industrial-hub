package com.industrialhub.backend.common.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.industrialhub.backend.common.domain.AuditAction;
import com.industrialhub.backend.common.domain.AuditLog;
import com.industrialhub.backend.common.infrastructure.AuditLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    @Mock
    private AuditLogRepository repository;

    private AuditService service;

    @BeforeEach
    void setUp() {
        service = new AuditService(repository, new ObjectMapper());
    }

    @Test
    void log_persistsEntryWithCorrectFields() {
        UUID entityId = UUID.randomUUID();
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.log("supervisor1", AuditAction.NC_CREATED, "NonConformance",
                entityId, Map.of("severity", "HIGH"));

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(repository).save(captor.capture());

        AuditLog saved = captor.getValue();
        assertThat(saved.getUsername()).isEqualTo("supervisor1");
        assertThat(saved.getAction()).isEqualTo(AuditAction.NC_CREATED);
        assertThat(saved.getEntityType()).isEqualTo("NonConformance");
        assertThat(saved.getEntityId()).isEqualTo(entityId.toString());
        assertThat(saved.getDetails()).contains("HIGH");
        assertThat(saved.getTimestamp()).isNotNull();
    }

    @Test
    void log_withNullDetails_persistsNullDetailsField() {
        UUID entityId = UUID.randomUUID();
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.log("admin", AuditAction.EQUIPMENT_DELETED, "Equipment", entityId, null);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(repository).save(captor.capture());

        assertThat(captor.getValue().getDetails()).isNull();
    }

    @Test
    void log_withStringEntityId_persistsCorrectly() {
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.log("system", AuditAction.IMPORT_CREATED, "ImportBatch",
                "batch-123", Map.of("rowCount", 42));

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(repository).save(captor.capture());

        assertThat(captor.getValue().getEntityId()).isEqualTo("batch-123");
        assertThat(captor.getValue().getDetails()).contains("42");
    }
}
