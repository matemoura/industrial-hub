package com.industrialhub.backend.common.auth.application.usecase;

import com.industrialhub.backend.common.auth.application.dto.UserModulePermissionResponse;
import com.industrialhub.backend.common.auth.domain.Role;
import com.industrialhub.backend.common.auth.domain.User;
import com.industrialhub.backend.common.auth.domain.UserNotFoundException;
import com.industrialhub.backend.common.auth.infrastructure.UserModulePermissionRepository;
import com.industrialhub.backend.common.auth.infrastructure.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class GetUserPermissionsUseCase {

    private final UserRepository userRepository;
    private final UserModulePermissionRepository permissionRepository;

    public GetUserPermissionsUseCase(UserRepository userRepository,
                                     UserModulePermissionRepository permissionRepository) {
        this.userRepository = userRepository;
        this.permissionRepository = permissionRepository;
    }

    public List<UserModulePermissionResponse> execute(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        if (user.getRole() == Role.ADMIN) {
            return List.of();
        }

        return permissionRepository.findByUserId(userId).stream()
                .map(UserModulePermissionResponse::from)
                .toList();
    }
}
