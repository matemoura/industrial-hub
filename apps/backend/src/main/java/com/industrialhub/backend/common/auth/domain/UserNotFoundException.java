package com.industrialhub.backend.common.auth.domain;

import java.util.UUID;

public class UserNotFoundException extends RuntimeException {
    public UserNotFoundException(UUID id) {
        super("Usuário não encontrado: " + id);
    }
}
