package com.industrialhub.backend.common.application;

import com.industrialhub.backend.common.auth.domain.Role;
import com.industrialhub.backend.common.auth.infrastructure.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class EmailEscalationService {

    private static final Logger log = LoggerFactory.getLogger(EmailEscalationService.class);

    // mailSender is optional — cannot use constructor injection for optional beans
    // because Spring would fail to start when no mail configuration is present.
    // Other fields are injected via constructor (see constructor below).
    @Autowired(required = false)
    private JavaMailSender mailSender;

    private final UserRepository userRepository;

    @Value("${mail.enabled:false}")
    private boolean mailEnabled;

    @Value("${spring.mail.username:noreply@industrialhub.com}")
    private String fromAddress;

    public EmailEscalationService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Async
    public void notifySlaBreached(String entityLabel, String entityId,
                                   String entityTitle, int slaHours) {
        if (!mailEnabled || mailSender == null) {
            log.info("[MAIL DISABLED] SLA vencido: {} '{}' ({}h)", entityLabel, entityTitle, slaHours);
            return;
        }

        List<String> emails = userRepository
            .findByRoleIn(List.of(Role.SUPERVISOR, Role.ADMIN))
            .stream()
            .map(u -> u.getEmail())
            .filter(e -> e != null && !e.isBlank())
            .toList();

        emails.forEach(email -> {
            try {
                SimpleMailMessage msg = new SimpleMailMessage();
                msg.setFrom(fromAddress);
                msg.setTo(email);
                msg.setSubject(String.format("[MSB] SLA Vencido: %s '%s'", entityLabel, entityTitle));
                msg.setText(String.format(
                    "O SLA da %s '%s' (ID: %s) foi ultrapassado.\n\n" +
                    "Prazo configurado: %dh\n\n" +
                    "Acesse o sistema para tomar as ações necessárias.",
                    entityLabel, entityTitle, entityId, slaHours
                ));
                mailSender.send(msg);
            } catch (Exception e) {
                log.warn("Falha ao enviar email de SLA vencido para {}: {}", email, e.getMessage());
            }
        });
    }
}
