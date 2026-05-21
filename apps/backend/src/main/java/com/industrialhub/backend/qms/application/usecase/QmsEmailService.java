package com.industrialhub.backend.qms.application.usecase;

import com.industrialhub.backend.common.auth.domain.Role;
import com.industrialhub.backend.common.auth.infrastructure.UserRepository;
import com.industrialhub.backend.qms.domain.NonConformance;
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
public class QmsEmailService {

    private static final Logger log = LoggerFactory.getLogger(QmsEmailService.class);

    @Autowired(required = false)
    private JavaMailSender mailSender;

    private final UserRepository userRepository;

    @Value("${mail.enabled:false}")
    private boolean mailEnabled;

    @Value("${app.mail.from:noreply@industrialhub.com}")
    private String fromAddress;

    public QmsEmailService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Async
    public void notifyCriticalNc(NonConformance nc) {
        if (!mailEnabled || mailSender == null) {
            log.info("[MAIL DISABLED] NC crítica registrada: '{}' por {}", nc.getTitle(), nc.getReportedBy());
            return;
        }

        List<String> emails = userRepository.findByRoleIn(List.of(Role.SUPERVISOR, Role.ADMIN)).stream()
                .map(u -> u.getEmail())
                .filter(e -> e != null && !e.isBlank())
                .toList();

        emails.forEach(email -> {
            try {
                SimpleMailMessage msg = new SimpleMailMessage();
                msg.setFrom(fromAddress);
                msg.setTo(email);
                msg.setSubject("[MSB QMS] NC CRÍTICA registrada: " + nc.getTitle());
                msg.setText(String.format(
                    "Uma não-conformidade crítica foi registrada no sistema MSB QMS.\n\n" +
                    "Título: %s\nRegistrado por: %s\nData: %s\n\n" +
                    "Acesse o sistema para iniciar a análise.",
                    nc.getTitle(), nc.getReportedBy(), nc.getReportedAt()
                ));
                mailSender.send(msg);
            } catch (Exception e) {
                log.error("Falha ao enviar email de NC crítica para {}: {}", email, e.getMessage());
            }
        });
    }

    @Async
    public void notifyNcClosed(NonConformance nc) {
        if (!mailEnabled || mailSender == null) {
            log.info("[MAIL DISABLED] NC fechada: '{}' (registrada por {})", nc.getTitle(), nc.getReportedBy());
            return;
        }

        userRepository.findByUsername(nc.getReportedBy()).ifPresent(user -> {
            if (user.getEmail() == null || user.getEmail().isBlank()) return;
            try {
                SimpleMailMessage msg = new SimpleMailMessage();
                msg.setFrom(fromAddress);
                msg.setTo(user.getEmail());
                msg.setSubject("[MSB QMS] NC fechada: " + nc.getTitle());
                msg.setText(String.format(
                    "A não-conformidade \"%s\" foi fechada por %s em %s.",
                    nc.getTitle(), nc.getClosedBy(), nc.getClosedAt()
                ));
                mailSender.send(msg);
            } catch (Exception e) {
                log.error("Falha ao enviar email de NC fechada para {}: {}", user.getEmail(), e.getMessage());
            }
        });
    }
}
