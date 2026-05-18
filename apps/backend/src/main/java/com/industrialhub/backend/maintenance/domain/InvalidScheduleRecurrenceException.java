package com.industrialhub.backend.maintenance.domain;

public class InvalidScheduleRecurrenceException extends RuntimeException {
    public InvalidScheduleRecurrenceException(String message) {
        super(message);
    }
}
