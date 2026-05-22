package com.industrialhub.backend.common.domain;

import java.util.UUID;

public class CannotAnonymizeActiveAdminException extends RuntimeException {

    public CannotAnonymizeActiveAdminException(UUID userId) {
        super("Não é possível anonimizar um ADMIN ativo (id=" + userId + ")");
    }
}
