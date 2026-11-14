package br.com.calendar.task;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskOccurrencesTest {

    private static Task task(String startsAt, String repeatInterval, Integer repeat) {
        Task task = new Task();
        task.setId("t1");
        task.setStartsAt(Instant.parse(startsAt));
        task.setRepeatInterval(repeatInterval);
        task.setRepeat(repeat);
        return task;
    }

    private static List<Instant> starts(Task task, String from, String to) {
        return TaskOccurrences.between(task, Instant.parse(from), Instant.parse(to)).stream()
                .map(TaskOccurrences.Occurrence::startsAt)
                .toList();
    }

    @Test
    void monthlyFromEndOfMonthClampsWithoutDrifting() {
        Task task = task("2026-01-31T10:00:00Z", "monthly", null);

        // Same result whatever range is asked, so calendar queries and
        // reminders (which use different ranges) agree on every occurrence.
        List<Instant> expected = List.of(
                Instant.parse("2026-02-28T10:00:00Z"),
                Instant.parse("2026-03-31T10:00:00Z"),
                Instant.parse("2026-04-30T10:00:00Z"));
        assertEquals(expected, starts(task, "2026-02-01T00:00:00Z", "2026-04-30T23:59:59Z"));
        assertEquals(List.of(Instant.parse("2026-03-31T10:00:00Z")),
                starts(task, "2026-03-01T00:00:00Z", "2026-03-31T23:59:59Z"));
    }

    @Test
    void yearlyFromLeapDayFallsBackToFebruary28() {
        Task task = task("2024-02-29T10:00:00Z", "yearly", null);

        assertEquals(List.of(Instant.parse("2027-02-28T10:00:00Z"), Instant.parse("2028-02-29T10:00:00Z")),
                starts(task, "2027-01-01T00:00:00Z", "2028-12-31T00:00:00Z"));
    }

    @Test
    void longRunningSeriesIsReachedWithoutWalkingItsHistory() {
        Task task = task("1990-01-01T15:00:00Z", "daily", 3);

        List<Instant> result = starts(task, "2026-11-14T00:00:00Z", "2026-11-20T00:00:00Z");

        assertEquals(2, result.size());
        result.forEach(start -> assertTrue(TaskOccurrences.isOccurrence(task, start)));
    }

    @Test
    void isOccurrenceFollowsTheSeries() {
        Task weekly = task("2026-11-02T15:00:00Z", "weekly", 2);

        assertTrue(TaskOccurrences.isOccurrence(weekly, Instant.parse("2026-11-02T15:00:00Z")));
        assertTrue(TaskOccurrences.isOccurrence(weekly, Instant.parse("2026-11-16T15:00:00Z")));
        assertFalse(TaskOccurrences.isOccurrence(weekly, Instant.parse("2026-11-09T15:00:00Z")));
        assertFalse(TaskOccurrences.isOccurrence(weekly, Instant.parse("2026-11-16T15:30:00Z")));
        assertFalse(TaskOccurrences.isOccurrence(weekly, Instant.parse("2026-10-19T15:00:00Z")));
        assertTrue(TaskOccurrences.isOccurrence(task("2026-01-31T10:00:00Z", "monthly", null),
                Instant.parse("2026-02-28T10:00:00Z")));
    }

    @Test
    void nonRecurringOrUnrecognizedIntervalOnlyMatchesItsOwnStart() {
        Task single = task("2026-11-02T15:00:00Z", null, null);
        Task unknown = task("2026-11-02T15:00:00Z", "fortnightly", null);

        assertFalse(TaskOccurrences.isRecurring(single));
        assertFalse(TaskOccurrences.isRecurring(unknown));
        assertTrue(TaskOccurrences.isOccurrence(unknown, Instant.parse("2026-11-02T15:00:00Z")));
        assertFalse(TaskOccurrences.isOccurrence(unknown, Instant.parse("2026-11-16T15:00:00Z")));
    }
}
