package com.industrialhub.backend.oee.application.validation;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DateRangeValidatorTest {

    private final DateRangeValidator validator = new DateRangeValidator();

    private static final LocalDate BASE = LocalDate.of(2026, 1, 1);

    @Test
    void validRange_doesNotThrow() {
        assertThatNoException().isThrownBy(() -> validator.validate(BASE, BASE.plusDays(30)));
    }

    @Test
    void sameDay_doesNotThrow() {
        assertThatNoException().isThrownBy(() -> validator.validate(BASE, BASE));
    }

    @Test
    void exactly366Days_doesNotThrow() {
        assertThatNoException().isThrownBy(() -> validator.validate(BASE, BASE.plusDays(366)));
    }

    @Test
    void reversedRange_throwsWithMessage() {
        assertThatThrownBy(() -> validator.validate(BASE.plusDays(1), BASE))
                .isInstanceOf(InvalidDateRangeException.class)
                .hasMessageContaining("startDate must not be after endDate");
    }

    @Test
    void rangeExceeds366Days_throwsWithMessage() {
        assertThatThrownBy(() -> validator.validate(BASE, BASE.plusDays(367)))
                .isInstanceOf(InvalidDateRangeException.class)
                .hasMessageContaining("Date range must not exceed 366 days");
    }
}
