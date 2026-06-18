package com.industrialhub.backend.common.auth.infrastructure;

import com.industrialhub.backend.common.auth.domain.Role;
import com.industrialhub.backend.common.auth.domain.User;
import com.industrialhub.backend.common.domain.AlertMetric;
import com.industrialhub.backend.common.domain.AlertThreshold;
import com.industrialhub.backend.common.domain.ConsentRecord;
import com.industrialhub.backend.common.domain.Plant;
import com.industrialhub.backend.common.domain.SlaClassifierField;
import com.industrialhub.backend.common.domain.SlaEntityType;
import com.industrialhub.backend.common.domain.SlaRule;
import com.industrialhub.backend.common.domain.UserPlant;
import com.industrialhub.backend.common.infrastructure.AlertThresholdRepository;
import com.industrialhub.backend.common.infrastructure.ConsentRecordRepository;
import com.industrialhub.backend.common.infrastructure.PlantRepository;
import com.industrialhub.backend.common.infrastructure.SlaRuleRepository;
import com.industrialhub.backend.common.infrastructure.UserPlantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@Order(2)
public class DataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AlertThresholdRepository alertThresholdRepository;
    private final SlaRuleRepository slaRuleRepository;
    private final PlantRepository plantRepository;
    private final UserPlantRepository userPlantRepository;
    private final ConsentRecordRepository consentRecordRepository;

    public DataInitializer(UserRepository userRepository,
                           PasswordEncoder passwordEncoder,
                           AlertThresholdRepository alertThresholdRepository,
                           SlaRuleRepository slaRuleRepository,
                           PlantRepository plantRepository,
                           UserPlantRepository userPlantRepository,
                           ConsentRecordRepository consentRecordRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.alertThresholdRepository = alertThresholdRepository;
        this.slaRuleRepository = slaRuleRepository;
        this.plantRepository = plantRepository;
        this.userPlantRepository = userPlantRepository;
        this.consentRecordRepository = consentRecordRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        createIfAbsent("admin", "admin", Role.ADMIN, "admin@msbbrasil.com");

        seedAlertThresholds();
        seedSlaRules();
        seedHqPlant();
        seedConsentRecords();
    }

    private void createIfAbsent(String username, String rawPassword, Role role, String email) {
        if (!userRepository.existsByUsername(username)) {
            userRepository.save(User.builder()
                    .username(username)
                    .password(passwordEncoder.encode(rawPassword))
                    .role(role)
                    .active(true)
                    .email(email)
                    .mustChangePassword(true) // SEC-047: force password change on first login
                    .build());
        }
    }

    private void seedAlertThresholds() {
        createThresholdIfAbsent(AlertMetric.OEE_AVG_BELOW, 0.65);
        createThresholdIfAbsent(AlertMetric.NC_CRITICAL_ABOVE, 3.0);
        createThresholdIfAbsent(AlertMetric.WO_URGENT_PENDING_HOURS, 4.0);
    }

    private void createThresholdIfAbsent(AlertMetric metric, double value) {
        if (alertThresholdRepository.findByMetricAndActiveTrue(metric).isEmpty()) {
            alertThresholdRepository.save(AlertThreshold.builder()
                    .metric(metric)
                    .threshold(value)
                    .emailEnabled(false)
                    .active(true)
                    .createdBy("system")
                    .updatedAt(LocalDateTime.now())
                    .build());
        }
    }

    private void seedSlaRules() {
        if (slaRuleRepository.count() > 0) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        slaRuleRepository.save(SlaRule.builder()
            .entityType(SlaEntityType.NC).classifierField(SlaClassifierField.SEVERITY)
            .classifierValue("CRITICAL").slaHours(48).escalateByEmail(true).createdAt(now).build());
        slaRuleRepository.save(SlaRule.builder()
            .entityType(SlaEntityType.NC).classifierField(SlaClassifierField.SEVERITY)
            .classifierValue("HIGH").slaHours(72).escalateByEmail(false).createdAt(now).build());
        slaRuleRepository.save(SlaRule.builder()
            .entityType(SlaEntityType.WORK_ORDER).classifierField(SlaClassifierField.PRIORITY)
            .classifierValue("URGENT").slaHours(4).escalateByEmail(true).createdAt(now).build());
        slaRuleRepository.save(SlaRule.builder()
            .entityType(SlaEntityType.WORK_ORDER).classifierField(SlaClassifierField.PRIORITY)
            .classifierValue("HIGH").slaHours(24).escalateByEmail(false).createdAt(now).build());
    }

    /** Creates ConsentRecord (v1.0) for every user that does not have one yet. US-067 AC#14. */
    private void seedConsentRecords() {
        List<User> allUsers = userRepository.findAll();
        LocalDateTime now = LocalDateTime.now();
        for (User user : allUsers) {
            if (!consentRecordRepository.existsByUsername(user.getUsername())) {
                consentRecordRepository.save(ConsentRecord.builder()
                    .username(user.getUsername())
                    .consentVersion("v1.0")
                    .consentedAt(now)
                    .ipAddress(null)
                    .build());
                log.info("ConsentRecord created for user '{}'", user.getUsername());
            }
        }
    }

    private void seedHqPlant() {
        Plant hq = plantRepository.findByCode("HQ").orElseGet(() -> {
            Plant newPlant = Plant.builder()
                .code("HQ")
                .name("Matriz")
                .timezone("America/Sao_Paulo")
                .active(true)
                .isDefault(true)
                .createdAt(LocalDateTime.now())
                .build();
            Plant saved = plantRepository.save(newPlant);
            log.info("Planta HQ criada com id={}", saved.getId());
            return saved;
        });

        // Associate all existing users to HQ plant if not already associated
        List<User> allUsers = userRepository.findAll();
        for (User user : allUsers) {
            if (!userPlantRepository.existsByUserIdAndPlantId(user.getId(), hq.getId())) {
                userPlantRepository.save(UserPlant.builder()
                    .user(user)
                    .plant(hq)
                    .build());
                log.info("Usuário '{}' associado à planta HQ", user.getUsername());
            }
        }
    }
}
