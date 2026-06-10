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
        doSave(username, action, entityType, entityId.toString(), details, null, null, null, null);
    }

    @Async
    public void log(String username, AuditAction action, String entityType,
                    String entityId, Map<String, Object> details) {
        doSave(username, action, entityType, entityId, details, null, null, null, null);
    }

    @Async
    public void log(String username, AuditAction action, String entityType,
                    UUID entityId, Map<String, Object> details, String module) {
        doSave(username, action, entityType, entityId.toString(), details, null, module, null, null);
    }

    @Async
    public void log(String username, AuditAction action, String entityType,
                    String entityId, Map<String, Object> details, String module) {
        doSave(username, action, entityType, entityId, details, null, module, null, null);
    }

    @Async
    public void log(String username, AuditAction action, String entityType,
                    UUID entityId, Map<String, Object> details, String module,
                    Object before, Object after) {
        doSave(username, action, entityType, entityId.toString(), details, null, module, before, after);
    }

    @Async
    public void log(String username, AuditAction action, String entityType,
                    String entityId, Map<String, Object> details, String module,
                    Object before, Object after) {
        doSave(username, action, entityType, entityId, details, null, module, before, after);
    }

    /**
     * Registra um evento de auditoria incluindo o endereço IP do solicitante.
     * Síncrono — use apenas quando o IP precisar ser persistido imediatamente (ex.: LOGIN_FAILED).
     */
    public void logWithIp(String username, AuditAction action, String entityType,
                          String entityId, Map<String, Object> details, String ipAddress) {
        doSave(username, action, entityType, entityId, details, ipAddress, null, null, null);
    }

    private void doSave(String username, AuditAction action, String entityType,
                        String entityId, Map<String, Object> details, String ipAddress,
                        String module, Object before, Object after) {
        String detailsJson = serialize(details, action);
        String beforeJson = serialize(before, action);
        String afterJson = serialize(after, action);

        AuditLog entry = AuditLog.builder()
                .timestamp(LocalDateTime.now())
                .username(username)
                .action(action)
                .entityType(entityType)
                .entityId(entityId)
                .details(detailsJson)
                .ipAddress(ipAddress)
                .module(module)
                .beforeState(beforeJson)
                .afterState(afterJson)
                .build();

        repository.save(entry);
    }

    private String serialize(Object obj, AuditAction action) {
        if (obj == null) return null;
        if (obj instanceof java.util.Map<?, ?> map && map.isEmpty()) return null;
        try {
            if (obj instanceof String s) return s;
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize audit object for action {}: {}", action, e.getMessage());
            return null;
        }
    }
}
