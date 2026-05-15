package com.industrialhub.backend.common.auth.domain;

public class UserAlreadyExistsException extends RuntimeException {
    public UserAlreadyExistsException(String username) {
        super("Username já existe: " + username);
    }
}
