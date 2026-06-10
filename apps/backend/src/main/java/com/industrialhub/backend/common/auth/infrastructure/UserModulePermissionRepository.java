package com.industrialhub.backend.common.auth.infrastructure;

import com.industrialhub.backend.common.auth.domain.AppModule;
import com.industrialhub.backend.common.auth.domain.UserModulePermission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserModulePermissionRepository extends JpaRepository<UserModulePermission, UUID> {
    List<UserModulePermission> findByUserId(UUID userId);
    Optional<UserModulePermission> findByUserIdAndModule(UUID userId, AppModule module);
    void deleteByUserId(UUID userId);
}
