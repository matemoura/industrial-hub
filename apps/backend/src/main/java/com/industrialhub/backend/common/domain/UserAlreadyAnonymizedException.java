package com.industrialhub.backend.common.domain;

public class UserAlreadyAnonymizedException extends RuntimeException {
    public UserAlreadyAnonymizedException() {
        super("Usuário já foi anonimizado");
    }
}
