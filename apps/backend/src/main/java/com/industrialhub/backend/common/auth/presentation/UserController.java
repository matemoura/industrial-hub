package com.industrialhub.backend.common.auth.presentation;

import com.industrialhub.backend.common.application.dto.UserDataExportResponse;
import com.industrialhub.backend.common.application.usecase.DataExportUseCase;
import com.industrialhub.backend.common.application.usecase.ExportUserDataPdfUseCase;
import com.industrialhub.backend.common.auth.application.dto.*;
import com.industrialhub.backend.common.auth.application.usecase.*;
import com.industrialhub.backend.common.auth.application.usecase.GetUserPermissionsUseCase;
import com.industrialhub.backend.common.auth.application.usecase.UpdateUserPermissionsUseCase;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
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
    private final DataExportUseCase dataExportUseCase;
    private final GetUserPermissionsUseCase getUserPermissions;
    private final UpdateUserPermissionsUseCase updateUserPermissions;
    private final ExportUserDataPdfUseCase exportUserDataPdf;

    public UserController(GetUserListUseCase getUserList,
                          CreateUserUseCase createUser,
                          UpdateUserRoleUseCase updateUserRole,
                          DeactivateUserUseCase deactivateUser,
                          ReactivateUserUseCase reactivateUser,
                          ChangeOwnPasswordUseCase changeOwnPassword,
                          DataExportUseCase dataExportUseCase,
                          GetUserPermissionsUseCase getUserPermissions,
                          UpdateUserPermissionsUseCase updateUserPermissions,
                          ExportUserDataPdfUseCase exportUserDataPdf) {
        this.getUserList = getUserList;
        this.createUser = createUser;
        this.updateUserRole = updateUserRole;
        this.deactivateUser = deactivateUser;
        this.reactivateUser = reactivateUser;
        this.changeOwnPassword = changeOwnPassword;
        this.dataExportUseCase = dataExportUseCase;
        this.getUserPermissions = getUserPermissions;
        this.updateUserPermissions = updateUserPermissions;
        this.exportUserDataPdf = exportUserDataPdf;
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

    @GetMapping("/api/v1/admin/users/{id}/permissions")
    @PreAuthorize("hasRole('ADMIN')")
    public List<UserModulePermissionResponse> getUserPermissions(@PathVariable UUID id) {
        return getUserPermissions.execute(id);
    }

    @PutMapping("/api/v1/admin/users/{id}/permissions")
    @PreAuthorize("hasRole('ADMIN')")
    public List<UserModulePermissionResponse> updateUserPermissions(@PathVariable UUID id,
                                                                     @Valid @RequestBody UpdateUserPermissionsRequest request,
                                                                     Authentication auth) {
        return updateUserPermissions.execute(id, request, auth.getName());
    }

    /**
     * GET /api/v1/users/me/data-export
     * Exports all personal data for the authenticated user as a PDF attachment.
     */
    @GetMapping("/api/v1/users/me/data-export")
    public ResponseEntity<byte[]> exportMyData(Authentication auth) {
        try {
            UserDataExportResponse data = dataExportUseCase.execute(auth.getName());
            byte[] pdfBytes = exportUserDataPdf.execute(data);
            String safeUsername = auth.getName().replaceAll("[^a-zA-Z0-9_\\-]", "_");
            String filename = "dados-pessoais-" + safeUsername + "-" + LocalDate.now() + ".pdf";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDisposition(ContentDisposition.attachment().filename(filename).build());
            return ResponseEntity.ok().headers(headers).body(pdfBytes);
        } catch (java.io.IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}
