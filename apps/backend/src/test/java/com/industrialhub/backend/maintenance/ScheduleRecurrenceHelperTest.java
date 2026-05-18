package com.industrialhub.backend.maintenance;

import com.industrialhub.backend.maintenance.application.usecase.ScheduleRecurrenceHelper;
import com.industrialhub.backend.maintenance.domain.InvalidScheduleRecurrenceException;
import com.industrialhub.backend.maintenance.domain.ScheduleRecurrence;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScheduleRecurrenceHelperTest {

    // --- DAILY ---

    @Test
    void daily_shouldReturnTomorrow() {
        LocalDate today = LocalDate.of(2026, 5, 20);
        LocalDate next = ScheduleRecurrenceHelper.calculateNext(today, ScheduleRecurrence.DAILY, null, null);
        assertThat(next).isEqualTo(LocalDate.of(2026, 5, 21));
    }

    // --- WEEKLY ---

    @Test
    void weekly_shouldReturnThisWeekDay_whenNotYetPassed() {
        // Wednesday May 20; dayOfWeek=5 (Friday) → May 22
        LocalDate from = LocalDate.of(2026, 5, 20); // Wednesday
        LocalDate next = ScheduleRecurrenceHelper.calculateNext(from, ScheduleRecurrence.WEEKLY, 5, null);
        assertThat(next).isEqualTo(LocalDate.of(2026, 5, 22)); // Friday
    }

    @Test
    void weekly_shouldReturnNextWeek_whenDayAlreadyPassed() {
        // Wednesday May 20; dayOfWeek=1 (Monday) already passed → next Monday May 25
        LocalDate from = LocalDate.of(2026, 5, 20); // Wednesday
        LocalDate next = ScheduleRecurrenceHelper.calculateNext(from, ScheduleRecurrence.WEEKLY, 1, null);
        assertThat(next).isEqualTo(LocalDate.of(2026, 5, 25)); // next Monday
    }

    @Test
    void weekly_shouldReturnNextWeek_whenTodayIsExactDay() {
        // Wednesday May 20; dayOfWeek=3 (Wednesday) → next Wednesday May 27
        LocalDate from = LocalDate.of(2026, 5, 20); // Wednesday=3
        LocalDate next = ScheduleRecurrenceHelper.calculateNext(from, ScheduleRecurrence.WEEKLY, 3, null);
        assertThat(next).isEqualTo(LocalDate.of(2026, 5, 27));
    }

    // --- MONTHLY ---

    @Test
    void monthly_shouldReturnNextMonthWithDayOfMonth() {
        LocalDate from = LocalDate.of(2026, 5, 20);
        LocalDate next = ScheduleRecurrenceHelper.calculateNext(from, ScheduleRecurrence.MONTHLY, null, 15);
        assertThat(next).isEqualTo(LocalDate.of(2026, 6, 15));
    }

    @Test
    void monthly_shouldClampToLastDayOfMonth_forShortMonth() {
        // dayOfMonth=28, February has 28 days in 2026 → Feb 28
        LocalDate from = LocalDate.of(2026, 1, 15);
        LocalDate next = ScheduleRecurrenceHelper.calculateNext(from, ScheduleRecurrence.MONTHLY, null, 28);
        assertThat(next).isEqualTo(LocalDate.of(2026, 2, 28));
    }

    // --- VALIDATION ---

    @Test
    void validate_weekly_shouldThrow_whenDayOfWeekNull() {
        assertThatThrownBy(() ->
                ScheduleRecurrenceHelper.validate(ScheduleRecurrence.WEEKLY, null, null))
                .isInstanceOf(InvalidScheduleRecurrenceException.class)
                .hasMessageContaining("dayOfWeek");
    }

    @Test
    void validate_weekly_shouldThrow_whenDayOfWeekOutOfRange() {
        assertThatThrownBy(() ->
                ScheduleRecurrenceHelper.validate(ScheduleRecurrence.WEEKLY, 8, null))
                .isInstanceOf(InvalidScheduleRecurrenceException.class);
    }

    @Test
    void validate_monthly_shouldThrow_whenDayOfMonthNull() {
        assertThatThrownBy(() ->
                ScheduleRecurrenceHelper.validate(ScheduleRecurrence.MONTHLY, null, null))
                .isInstanceOf(InvalidScheduleRecurrenceException.class)
                .hasMessageContaining("dayOfMonth");
    }

    @Test
    void validate_monthly_shouldThrow_whenDayOfMonthAbove28() {
        assertThatThrownBy(() ->
                ScheduleRecurrenceHelper.validate(ScheduleRecurrence.MONTHLY, null, 29))
                .isInstanceOf(InvalidScheduleRecurrenceException.class);
    }

    @Test
    void validate_daily_shouldPass_withNullDayFields() {
        // Should not throw
        ScheduleRecurrenceHelper.validate(ScheduleRecurrence.DAILY, null, null);
    }
}
