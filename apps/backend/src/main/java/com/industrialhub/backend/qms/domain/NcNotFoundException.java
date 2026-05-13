package com.industrialhub.backend.qms.domain;

import java.util.UUID;

public class NcNotFoundException extends RuntimeException {

    public NcNotFoundException(UUID id) {
        super("NC não encontrada: " + id);
    }
}
