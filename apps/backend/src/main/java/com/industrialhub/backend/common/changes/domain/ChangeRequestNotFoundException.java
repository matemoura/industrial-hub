package com.industrialhub.backend.common.changes.domain;

import java.util.UUID;

public class ChangeRequestNotFoundException extends RuntimeException {
    public ChangeRequestNotFoundException(UUID id) {
        super("Change request not found: " + id);
    }
}
