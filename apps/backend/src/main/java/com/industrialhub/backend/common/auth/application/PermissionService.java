package com.industrialhub.backend.common.auth.application;

import com.industrialhub.backend.common.auth.domain.AppModule;
import com.industrialhub.backend.common.auth.domain.Role;
import com.industrialhub.backend.common.auth.domain.User;
import com.industrialhub.backend.common.auth.domain.UserModulePermission;
import com.industrialhub.backend.common.auth.infrastructure.UserModulePermissionRepository;
import com.industrialhub.backend.common.auth.infrastructure.UserRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service("perm")
public class PermissionService {

    private final UserRepository userRepository;
    private final UserModulePermissionRepository permissionRepository;

    public PermissionService(UserRepository userRepository,
                             UserModulePermissionRepository permissionRepository) {
        this.userRepository = userRepository;
        this.permissionRepository = permissionRepository;
    }

    public boolean canView(String username, AppModule module) {
        return isAdminOrHas(username, module, p -> p.isCanView());
    }

    public boolean canCreate(String username, AppModule module) {
        return isAdminOrHas(username, module, p -> p.isCanCreate());
    }

    public boolean canEdit(String username, AppModule module) {
        return isAdminOrHas(username, module, p -> p.isCanEdit());
    }

    public boolean canDelete(String username, AppModule module) {
        return isAdminOrHas(username, module, p -> p.isCanDelete());
    }

    private boolean isAdminOrHas(String username, AppModule module, PermissionCheck check) {
        Optional<User> userOpt = userRepository.findByUsername(username);
        if (userOpt.isEmpty()) return false;
        User user = userOpt.get();
        if (user.getRole() == Role.ADMIN) return true;
        return permissionRepository.findByUserIdAndModule(user.getId(), module)
                .map(check::test)
                .orElse(false);
    }

    @FunctionalInterface
    private interface PermissionCheck {
        boolean test(UserModulePermission permission);
    }
}
