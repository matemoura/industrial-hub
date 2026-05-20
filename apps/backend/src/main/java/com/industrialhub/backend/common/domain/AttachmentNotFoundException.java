package com.industrialhub.backend.common.domain;

import java.util.UUID;

public class AttachmentNotFoundException extends RuntimeException {
    public AttachmentNotFoundException(UUID id) {
        super("Attachment not found: " + id);
    }
}
