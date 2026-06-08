package com.industrialhub.backend.common.changes.domain;

import java.util.UUID;

public class ChangeRequestLinkNotFoundException extends RuntimeException {
    public ChangeRequestLinkNotFoundException(UUID id) {
        super("Change request link not found: " + id);
    }
}
