package com.industrialhub.backend.common.changes.domain;

public class InvalidChangeStatusTransitionException extends RuntimeException {
    public InvalidChangeStatusTransitionException(ChangeStatus current, ChangeStatus target) {
        super("Invalid change request status transition: " + current + " → " + target);
    }
    public InvalidChangeStatusTransitionException(String message) {
        super(message);
    }
}
