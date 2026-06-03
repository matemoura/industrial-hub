package com.industrialhub.backend.qms.ged.domain;

/**
 * SEC-129: Thrown when attempting to create a GED document with a code
 * that already exists. Maps to HTTP 409 Conflict.
 */
public class DocumentCodeAlreadyExistsException extends RuntimeException {

    public DocumentCodeAlreadyExistsException(String code) {
        super("Já existe um documento com o código: " + code);
    }
}
