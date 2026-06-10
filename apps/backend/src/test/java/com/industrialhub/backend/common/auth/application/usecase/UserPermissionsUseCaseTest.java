package com.industrialhub.backend.common.auth.application.usecase;

import com.industrialhub.backend.common.application.AuditService;
import com.industrialhub.backend.common.auth.application.PermissionService;
import com.industrialhub.backend.common.auth.application.dto.UpdateUserPermissionsRequest;
import com.industrialhub.backend.common.auth.application.dto.UserModulePermissionResponse;
import com.industrialhub.backend.common.auth.domain.AppModule;
import com.industrialhub.backend.common.auth.domain.Role;
import com.industrialhub.backend.common.auth.domain.User;
import com.industrialhub.backend.common.auth.domain.UserModulePermission;
import com.industrialhub.backend.common.auth.infrastructure.UserModulePermissionRepository;
import com.industrialhub.backend.common.auth.infrastructure.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserPermissionsUseCaseTest {

    @Mock private UserRepository userRepository;
    @Mock private UserModulePermissionRepository permissionRepository;
    @Mock private AuditService auditService;

    private static final UUID USER_ID = UUID.randomUUID();

    @Test
    void admin_temCanViewTrueParaQualquerModulo() {
        User admin = User.builder().id(USER_ID).username("admin")
                .role(Role.ADMIN).active(true).build();
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(admin));

        PermissionService service = new PermissionService(userRepository, permissionRepository);

        assertThat(service.canView("admin", AppModule.QMS)).isTrue();
        assertThat(service.canCreate("admin", AppModule.MAINTENANCE)).isTrue();
        assertThat(service.canEdit("admin", AppModule.PRODUCTION)).isTrue();
        assertThat(service.canDelete("admin", AppModule.OEE)).isTrue();
        verify(permissionRepository, never()).findByUserIdAndModule(any(), any());
    }

    @Test
    void updateUserPermissions_substituiPermissoesAtomicamente() {
        User user = User.builder().id(USER_ID).username("joao")
                .role(Role.USER).active(true).build();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        UserModulePermission savedPerm = UserModulePermission.builder()
                .id(UUID.randomUUID()).user(user)
                .module(AppModule.QMS).canView(true).canCreate(true).canEdit(false).canDelete(false)
                .build();
        when(permissionRepository.save(any())).thenReturn(savedPerm);

        UpdateUserPermissionsUseCase useCase = new UpdateUserPermissionsUseCase(
                userRepository, permissionRepository, auditService);

        var request = new UpdateUserPermissionsRequest(List.of(
                new UserModulePermissionResponse(AppModule.QMS, true, true, false, false)
        ));

        List<UserModulePermissionResponse> result = useCase.execute(USER_ID, request, "admin");

        verify(permissionRepository).deleteByUserId(USER_ID);
        verify(permissionRepository, times(1)).save(any());
        assertThat(result).hasSize(1);
        assertThat(result.get(0).module()).isEqualTo(AppModule.QMS);
    }

    @Test
    void getUserPermissions_retornaListaCorreta() {
        User user = User.builder().id(USER_ID).username("ana")
                .role(Role.USER).active(true).build();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        UserModulePermission perm = UserModulePermission.builder()
                .id(UUID.randomUUID()).user(user)
                .module(AppModule.MAINTENANCE).canView(true).canCreate(false).canEdit(false).canDelete(false)
                .build();
        when(permissionRepository.findByUserId(USER_ID)).thenReturn(List.of(perm));

        GetUserPermissionsUseCase useCase = new GetUserPermissionsUseCase(userRepository, permissionRepository);

        List<UserModulePermissionResponse> result = useCase.execute(USER_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).module()).isEqualTo(AppModule.MAINTENANCE);
        assertThat(result.get(0).canView()).isTrue();
        assertThat(result.get(0).canCreate()).isFalse();
    }

    @Test
    void usuarioSemPermissao_temCanViewFalseParaModuloNaoConfigurado() {
        User user = User.builder().id(USER_ID).username("carlos")
                .role(Role.USER).active(true).build();
        when(userRepository.findByUsername("carlos")).thenReturn(Optional.of(user));
        when(permissionRepository.findByUserIdAndModule(USER_ID, AppModule.TRAINING))
                .thenReturn(Optional.empty());

        PermissionService service = new PermissionService(userRepository, permissionRepository);

        assertThat(service.canView("carlos", AppModule.TRAINING)).isFalse();
        assertThat(service.canCreate("carlos", AppModule.TRAINING)).isFalse();
    }
}
