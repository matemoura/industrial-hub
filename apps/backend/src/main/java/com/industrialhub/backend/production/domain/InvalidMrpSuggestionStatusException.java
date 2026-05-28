package com.industrialhub.backend.production.domain;

public class InvalidMrpSuggestionStatusException extends RuntimeException {
    public InvalidMrpSuggestionStatusException(String message) {
        super(message);
    }
}
