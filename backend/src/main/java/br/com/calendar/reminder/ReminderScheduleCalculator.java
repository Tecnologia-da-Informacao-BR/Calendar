package br.com.calendar.reminder;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Component
public class ReminderScheduleCalculator {

    public Instant calculateScheduledAt(Instant occurrenceStartsAt, int reminderMinutesBefore) {
        if (occurrenceStartsAt == null) {
            throw new IllegalArgumentException("Start time is mandatory");
        }
        if (reminderMinutesBefore < 0 || reminderMinutesBefore > 525600) {
            throw new IllegalArgumentException("the interval must be between 0 and 365 days");
        }
        return occurrenceStartsAt.minus(reminderMinutesBefore, ChronoUnit.MINUTES);
    }

}
