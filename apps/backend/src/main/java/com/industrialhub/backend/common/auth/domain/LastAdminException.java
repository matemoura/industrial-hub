package com.industrialhub.backend.common.auth.domain;

public class LastAdminException extends RuntimeException {
    public LastAdminException() {
        super("Não é possível desativar o único administrador ativo");
    }
}
