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
        doSave(username, action, entityType, entityId.toString(), details, null);
    }

    @Async
    public void log(String username, AuditAction action, String entityType,
                    String entityId, Map<String, Object> details) {
        doSave(username, action, entityType, entityId, details, null);
    }

    /**
     * Registra um evento de auditoria incluindo o endereço IP do solicitante.
     *
     * <p><strong>Este método é síncrono e bloqueia a thread do caller</strong> —
     * ao contrário das sobrecargas {@link #log(String, AuditAction, String, UUID, Map)}
     * e {@link #log(String, AuditAction, String, String, Map)}, que são anotadas com
     * {@code @Async} e executam em thread separada do pool de tarefas assíncronas.
     * Use este método apenas quando o IP precisar ser persistido imediatamente antes
     * de devolver a resposta ao cliente (ex.: {@code LOGIN_FAILED}).</p>
     *
     * @param username    nome do usuário que realizou a ação
     * @param action      tipo de ação auditada
     * @param entityType  tipo da entidade afetada (ex.: {@code "Auth"})
     * @param entityId    identificador da entidade afetada
     * @param details     mapa de detalhes adicionais (pode ser {@code null})
     * @param ipAddress   endereço IP do cliente; persistido na coluna dedicada {@code ip_address}
     */
    public void logWithIp(String username, AuditAction action, String entityType,
                          String entityId, Map<String, Object> details, String ipAddress) {
        doSave(username, action, entityType, entityId, details, ipAddress);
    }

    private void doSave(String username, AuditAction action, String entityType,
                        String entityId, Map<String, Object> details, String ipAddress) {
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
                .ipAddress(ipAddress)
                .build();

        repository.save(entry);
    }
}
