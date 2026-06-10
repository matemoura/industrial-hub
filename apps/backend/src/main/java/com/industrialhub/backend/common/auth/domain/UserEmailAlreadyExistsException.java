package com.industrialhub.backend.common.auth.domain;

public class UserEmailAlreadyExistsException extends RuntimeException {
    public UserEmailAlreadyExistsException(String email) {
        super("E-mail já cadastrado: " + email);
    }
}
