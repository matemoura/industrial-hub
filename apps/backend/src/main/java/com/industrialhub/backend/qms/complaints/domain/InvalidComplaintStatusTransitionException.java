package com.industrialhub.backend.qms.complaints.domain;

public class InvalidComplaintStatusTransitionException extends RuntimeException {
    public InvalidComplaintStatusTransitionException(String message) {
        super(message);
    }
}
