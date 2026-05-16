package com.industrialhub.backend.common.auth.infrastructure;

import com.industrialhub.backend.common.auth.domain.Role;
import com.industrialhub.backend.common.auth.domain.User;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@Order(2)
public class DataInitializer implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        createIfAbsent("admin", "admin", Role.ADMIN, "admin@msbbrasil.com");
        createIfAbsent("supervisor", "supervisor", Role.SUPERVISOR, "supervisor@msbbrasil.com");
        createIfAbsent("operator", "operator", Role.OPERATOR, "operator@msbbrasil.com");
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
}
