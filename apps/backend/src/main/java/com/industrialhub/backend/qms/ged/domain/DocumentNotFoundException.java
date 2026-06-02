package com.industrialhub.backend.qms.ged.domain;

import java.util.UUID;

public class DocumentNotFoundException extends RuntimeException {
    public DocumentNotFoundException(UUID id) {
        super("Documento não encontrado: " + id);
    }
}
