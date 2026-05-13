package com.industrialhub.backend.qms.domain;

import java.util.UUID;

public class ActionNotFoundException extends RuntimeException {

    public ActionNotFoundException(UUID id) {
        super("Ação corretiva não encontrada: " + id);
    }
}
