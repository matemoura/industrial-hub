package com.industrialhub.backend.production.domain;

import java.util.UUID;

public class MrpSuggestionNotFoundException extends RuntimeException {
    public MrpSuggestionNotFoundException(UUID id) {
        super("Sugestão MRP não encontrada: " + id);
    }
}
