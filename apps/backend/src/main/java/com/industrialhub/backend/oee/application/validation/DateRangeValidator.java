package com.industrialhub.backend.oee.application.validation;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@Component
public class DateRangeValidator {

    static final int MAX_DAYS = 366;

    public void validate(LocalDate startDate, LocalDate endDate) {
        if (startDate.isAfter(endDate)) {
            throw new InvalidDateRangeException("startDate must not be after endDate");
        }
        if (ChronoUnit.DAYS.between(startDate, endDate) > MAX_DAYS) {
            throw new InvalidDateRangeException("Date range must not exceed 366 days");
        }
    }
}
