package com.industrialhub.backend.common.auth.application.dto;

import com.industrialhub.backend.common.auth.domain.AppModule;
import com.industrialhub.backend.common.auth.domain.UserModulePermission;

public record UserModulePermissionResponse(
        AppModule module,
        boolean canView,
        boolean canCreate,
        boolean canEdit,
        boolean canDelete
) {
    public static UserModulePermissionResponse from(UserModulePermission p) {
        return new UserModulePermissionResponse(
                p.getModule(), p.isCanView(), p.isCanCreate(), p.isCanEdit(), p.isCanDelete());
    }
}
