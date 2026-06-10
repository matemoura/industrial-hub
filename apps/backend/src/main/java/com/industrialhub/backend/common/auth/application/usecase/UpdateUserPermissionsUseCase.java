package com.industrialhub.backend.common.auth.application.usecase;

import com.industrialhub.backend.common.application.AuditService;
import com.industrialhub.backend.common.auth.application.dto.UpdateUserPermissionsRequest;
import com.industrialhub.backend.common.auth.application.dto.UserModulePermissionResponse;
import com.industrialhub.backend.common.auth.domain.User;
import com.industrialhub.backend.common.auth.domain.UserModulePermission;
import com.industrialhub.backend.common.auth.domain.UserNotFoundException;
import com.industrialhub.backend.common.auth.infrastructure.UserModulePermissionRepository;
import com.industrialhub.backend.common.auth.infrastructure.UserRepository;
import com.industrialhub.backend.common.domain.AuditAction;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class UpdateUserPermissionsUseCase {

    private final UserRepository userRepository;
    private final UserModulePermissionRepository permissionRepository;
    private final AuditService auditService;

    public UpdateUserPermissionsUseCase(UserRepository userRepository,
                                        UserModulePermissionRepository permissionRepository,
                                        AuditService auditService) {
        this.userRepository = userRepository;
        this.permissionRepository = permissionRepository;
        this.auditService = auditService;
    }

    @Transactional
    public List<UserModulePermissionResponse> execute(UUID userId,
                                                       UpdateUserPermissionsRequest request,
                                                       String adminUsername) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        permissionRepository.deleteByUserId(userId);

        List<UserModulePermission> saved = request.permissions().stream()
                .map(dto -> UserModulePermission.builder()
                        .user(user)
                        .module(dto.module())
                        .canView(dto.canView())
                        .canCreate(dto.canCreate())
                        .canEdit(dto.canEdit())
                        .canDelete(dto.canDelete())
                        .build())
                .map(permissionRepository::save)
                .toList();

        auditService.log(adminUsername, AuditAction.USER_PERMISSIONS_UPDATED, "UserModulePermission",
                userId, Map.of("modules", saved.size()));

        return saved.stream().map(UserModulePermissionResponse::from).toList();
    }
}
