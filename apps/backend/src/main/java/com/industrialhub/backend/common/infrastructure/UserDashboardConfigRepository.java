package com.industrialhub.backend.common.infrastructure;

import com.industrialhub.backend.common.domain.UserDashboardConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserDashboardConfigRepository extends JpaRepository<UserDashboardConfig, UUID> {

    Optional<UserDashboardConfig> findByUsername(String username);

    void deleteByUsername(String username);
}
