package com.industrialhub.backend.common.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.industrialhub.backend.common.domain.AuditAction;
import com.industrialhub.backend.common.domain.AuditLog;
import com.industrialhub.backend.common.infrastructure.AuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditLogRepository repository;
    private final ObjectMapper objectMapper;

    public AuditService(AuditLogRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Async
    public void log(String username, AuditAction action, String entityType,
                    UUID entityId, Map<String, Object> details) {
        log(username, action, entityType, entityId.toString(), details);
    }

    @Async
    public void log(String username, AuditAction action, String entityType,
                    String entityId, Map<String, Object> details) {
        String detailsJson = null;
        if (details != null && !details.isEmpty()) {
            try {
                detailsJson = objectMapper.writeValueAsString(details);
            } catch (JsonProcessingException e) {
                log.warn("Failed to serialize audit details for action {}: {}", action, e.getMessage());
            }
        }

        AuditLog entry = AuditLog.builder()
                .timestamp(LocalDateTime.now())
                .username(username)
                .action(action)
                .entityType(entityType)
                .entityId(entityId)
                .details(detailsJson)
                .build();

        repository.save(entry);
    }
}
