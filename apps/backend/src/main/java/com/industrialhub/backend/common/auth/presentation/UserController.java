package com.industrialhub.backend.common.auth.presentation;

import com.industrialhub.backend.common.auth.application.dto.*;
import com.industrialhub.backend.common.auth.application.usecase.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
public class UserController {

    private final GetUserListUseCase getUserList;
    private final CreateUserUseCase createUser;
    private final UpdateUserRoleUseCase updateUserRole;
    private final DeactivateUserUseCase deactivateUser;
    private final ReactivateUserUseCase reactivateUser;
    private final ChangeOwnPasswordUseCase changeOwnPassword;

    public UserController(GetUserListUseCase getUserList,
                          CreateUserUseCase createUser,
                          UpdateUserRoleUseCase updateUserRole,
                          DeactivateUserUseCase deactivateUser,
                          ReactivateUserUseCase reactivateUser,
                          ChangeOwnPasswordUseCase changeOwnPassword) {
        this.getUserList = getUserList;
        this.createUser = createUser;
        this.updateUserRole = updateUserRole;
        this.deactivateUser = deactivateUser;
        this.reactivateUser = reactivateUser;
        this.changeOwnPassword = changeOwnPassword;
    }

    @GetMapping("/api/v1/admin/users")
    @PreAuthorize("hasRole('ADMIN')")
    public List<UserResponse> listUsers() {
        return getUserList.execute();
    }

    @PostMapping("/api/v1/admin/users")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse createUser(@Valid @RequestBody CreateUserRequest request,
                                   Authentication auth) {
        return createUser.execute(request, auth.getName());
    }

    @PutMapping("/api/v1/admin/users/{id}/role")
    @PreAuthorize("hasRole('ADMIN')")
    public UserResponse updateRole(@PathVariable UUID id,
                                   @Valid @RequestBody UpdateUserRoleRequest request,
                                   Authentication auth) {
        return updateUserRole.execute(id, request, auth.getName());
    }

    @PutMapping("/api/v1/admin/users/{id}/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivate(@PathVariable UUID id, Authentication auth) {
        deactivateUser.execute(id, auth.getName());
    }

    @PutMapping("/api/v1/admin/users/{id}/reactivate")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reactivate(@PathVariable UUID id, Authentication auth) {
        reactivateUser.execute(id, auth.getName());
    }

    @PutMapping("/api/v1/users/me/password")
    public ResponseEntity<LoginResponseDto> changePassword(@Valid @RequestBody ChangePasswordRequest request,
                                                           Authentication auth) {
        return ResponseEntity.ok(changeOwnPassword.execute(auth.getName(), request));
    }
}
