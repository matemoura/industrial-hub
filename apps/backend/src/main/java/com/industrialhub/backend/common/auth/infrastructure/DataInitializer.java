package com.industrialhub.backend.common.auth.infrastructure;

import com.industrialhub.backend.common.auth.domain.Role;
import com.industrialhub.backend.common.auth.domain.User;
import com.industrialhub.backend.common.domain.AlertMetric;
import com.industrialhub.backend.common.domain.AlertThreshold;
import com.industrialhub.backend.common.infrastructure.AlertThresholdRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@Order(2)
public class DataInitializer implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AlertThresholdRepository alertThresholdRepository;

    public DataInitializer(UserRepository userRepository,
                           PasswordEncoder passwordEncoder,
                           AlertThresholdRepository alertThresholdRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.alertThresholdRepository = alertThresholdRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        createIfAbsent("admin", "admin", Role.ADMIN, "admin@msbbrasil.com");
        createIfAbsent("supervisor", "supervisor", Role.SUPERVISOR, "supervisor@msbbrasil.com");
        createIfAbsent("operator", "operator", Role.OPERATOR, "operator@msbbrasil.com");

        seedAlertThresholds();
    }

    private void createIfAbsent(String username, String rawPassword, Role role, String email) {
        if (!userRepository.existsByUsername(username)) {
            userRepository.save(User.builder()
                    .username(username)
                    .password(passwordEncoder.encode(rawPassword))
                    .role(role)
                    .active(true)
                    .email(email)
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
}
