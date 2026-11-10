package br.com.calendar.reminder;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ReminderScheduleCalculatorTest {

    @Test
    public void subtractsReminderOffsetFromOccurrenceStart() {

        ReminderScheduleCalculator calculator = new ReminderScheduleCalculator();
        Instant occurrenceStartsAt = Instant.parse("2026-11-10T18:00:00Z");
        Instant scheduledAt = calculator.calculateScheduledAt(occurrenceStartsAt, 45);

        assertEquals(Instant.parse("2026-11-10T17:15:00Z"), scheduledAt);
    }

    @Test
    public void returnsOccurrenceStartForZeroOffset() {

        ReminderScheduleCalculator calculator = new ReminderScheduleCalculator();
        Instant occurrenceStartsAt = Instant.parse("2026-11-10T18:00:00Z");
        Instant scheduledAt = calculator.calculateScheduledAt(occurrenceStartsAt, 0);

        assertEquals(occurrenceStartsAt, scheduledAt);
    }

    @Test
    public void acceptsMaximumReminderOffset() {

        ReminderScheduleCalculator calculator = new ReminderScheduleCalculator();
        Instant occurrenceStartsAt = Instant.parse("2026-11-10T18:00:00Z");
        Instant scheduledAt = calculator.calculateScheduledAt(occurrenceStartsAt, 525600);

        assertEquals(Instant.parse("2025-11-10T18:00:00Z"), scheduledAt);
    }

    @Test
    public void rejectsNegativeReminderOffset() {

        ReminderScheduleCalculator calculator = new ReminderScheduleCalculator();
        Instant occurrenceStartsAt = Instant.parse("2026-11-10T18:00:00Z");

        assertThrows(IllegalArgumentException.class, () -> {
            calculator.calculateScheduledAt(occurrenceStartsAt, -1);
        });
    }

    @Test
    public void rejectsOffsetAboveMaximum() {

        ReminderScheduleCalculator calculator = new ReminderScheduleCalculator();
        Instant occurrenceStartsAt = Instant.parse("2026-11-10T18:00:00Z");

        assertThrows(IllegalArgumentException.class, () -> {
            calculator.calculateScheduledAt(occurrenceStartsAt, 525601);
        });
    }

    @Test
    public void rejectsMissingOccurrenceStart() {

        ReminderScheduleCalculator calculator = new ReminderScheduleCalculator();
        Instant occurrenceStartsAt = null;

        assertThrows(IllegalArgumentException.class, () -> {
            calculator.calculateScheduledAt(occurrenceStartsAt, 45);
        });
    }
}